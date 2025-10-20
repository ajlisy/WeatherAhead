/*
 WeatherAhead - 8hr Precipitation Driver

 Hubitat driver that exposes precipitation intensity and probability for the next 8 hours.
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME = 'WeatherAhead Driver'
@Field static final String DRIVER_NAMESPACE = 'ajlisy'
@Field static final String DRIVER_AUTHOR = 'ajlisy'
@Field static final String DRIVER_VERSION = '0.1.0'

metadata {
    definition(name: DRIVER_NAME, namespace: DRIVER_NAMESPACE, author: DRIVER_AUTHOR) {
        capability 'Sensor'
        capability 'Refresh'

        attribute 'precipitationExpected', 'boolean'
        attribute 'precipitationIntensity', 'number'
        attribute 'precipitationIntensityText', 'string'
        attribute 'lastUpdated', 'string'
        attribute 'status', 'string'

        command 'poll'
        command 'forcePoll'
    }
    preferences {
        input name: 'apiKey', type: 'text', title: 'OpenWeatherMap API Key', required: true
        input name: 'latitude', type: 'decimal', title: "Latitude (blank to use hub's)", required: false
        input name: 'longitude', type: 'decimal', title: "Longitude (blank to use hub's)", required: false
        input name: 'units', type: 'enum', title: 'Units', options: ['imperial','metric'], required: false
        input name: 'pollIntervalMins', type: 'number', title: 'Poll interval (minutes, min 5)', defaultValue: 15, range: '5..1440'
        input name: 'debugLogging', type: 'bool', title: 'Enable debug logging', defaultValue: false
    }
}

void installed() { initialize() }
void updated() { unschedule(); initialize() }
void uninstalled() { unschedule() }

void initialize() {
    Integer mins = safeInterval()
    runIn(2, 'poll')
    schedule("0 */${mins} * * * ?", poll)
}

Integer safeInterval() { Math.max(5, ((settings?.pollIntervalMins as Integer) ?: 15)) }
String resolvedUnits() { settings?.units ? (settings.units as String) : ((location.temperatureScale == 'F') ? 'imperial' : 'metric') }
Map<String,Object> resolvedLocation() {
    BigDecimal lat = (settings?.latitude != null) ? settings.latitude as BigDecimal : (location?.latitude as BigDecimal)
    BigDecimal lon = (settings?.longitude != null) ? settings.longitude as BigDecimal : (location?.longitude as BigDecimal)
    [lat:lat, lon:lon]
}
String buildUrl() {
    Map loc = resolvedLocation()
    String u = resolvedUnits()
    return "https://api.openweathermap.org/data/3.0/onecall?lat=${loc.lat}&lon=${loc.lon}&appid=${settings.apiKey}&units=${u}&exclude=minutely,current,daily,alerts"
}

void refresh() { poll() }

void forcePoll() {
    if (debugLogging) log.debug "Force poll requested"
    poll()
}

void poll() {
    if (!settings?.apiKey) { 
        sendEvent(name:'status', value:'Missing API key')
        if (debugLogging) log.debug "Poll failed: Missing API key"
        return 
    }
    String url = buildUrl()
    Map params = [uri:url, contentType:'application/json', timeout:30]
    if (debugLogging) log.debug "Making API call: ${url}"
    
    try {
        httpGet(params) { resp ->
            if (debugLogging) log.debug "API response status: ${resp?.status}"
            if (debugLogging) log.debug "API response data: ${resp?.data}"
            
            if (resp?.status != 200) { 
                sendEvent(name:'status', value:"HTTP ${resp?.status}")
                if (debugLogging) log.warn "API call failed with status ${resp?.status}"
                return 
            }
            Map data = resp.data as Map
            processHourly(data)
            sendEvent(name:'status', value:'OK')
            sendEvent(name:'lastUpdated', value: new Date().format("yyyy-MM-dd HH:mm:ss z", location.timeZone))
            if (debugLogging) log.debug "Successfully processed hourly precipitation data"
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        sendEvent(name:'status', value: (e?.statusCode==429 ? 'Rate limited (429)' : "HTTP error ${e?.statusCode}"))
        if (debugLogging) log.warn "HTTP error: ${e}"
    } catch (Exception ex) {
        sendEvent(name:'status', value:'Request failed')
        if (debugLogging) log.warn "Exception: ${ex.message}", ex
    }
}

Integer getIntensityFromConditionCode(Integer code) {
    if (code == null) return 0
    
    // Group 2xx (Thunderstorm): Heavy (3)
    if (code >= 200 && code < 300) return 3
    
    // Group 3xx (Drizzle)
    if (code >= 300 && code < 400) {
        if (code == 300 || code == 301 || code == 310) return 1 // Light
        if (code == 302 || code == 311 || code == 312 || code == 313 || code == 314 || code == 321) return 2 // Moderate
        return 1 // Default drizzle to light
    }
    
    // Group 5xx (Rain)
    if (code >= 500 && code < 600) {
        if (code == 500 || code == 520) return 1 // Light
        if (code == 501 || code == 511 || code == 521) return 2 // Moderate
        if (code == 502 || code == 503 || code == 504 || code == 522 || code == 531) return 3 // Heavy
        return 1 // Default rain to light
    }
    
    // Group 6xx (Snow)
    if (code >= 600 && code < 700) {
        if (code == 600 || code == 620) return 1 // Light
        if (code == 601 || code == 611 || code == 612 || code == 613 || code == 615 || code == 616 || code == 621) return 2 // Moderate
        if (code == 602 || code == 622) return 3 // Heavy
        return 1 // Default snow to light
    }
    
    // All other codes (clear, clouds, etc.): None (0)
    return 0
}

void processHourly(Map payload) {
    List hourly = (payload?.hourly as List) ?: []
    Integer count = Math.min(8, hourly.size())
    Integer maxIntensity = 0

    if (debugLogging) log.debug "Processing ${count} hourly entries for precipitation analysis"

    for (int i=0; i<count; i++) {
        Map h = (hourly[i] as Map) ?: [:]
        List weather = (h.weather as List) ?: []
        
        if (weather && weather[0] instanceof Map) {
            Map weatherData = weather[0] as Map
            Integer conditionCode = weatherData.id as Integer
            
            if (conditionCode != null) {
                Integer intensity = getIntensityFromConditionCode(conditionCode)
                maxIntensity = Math.max(maxIntensity, intensity)
                
                if (debugLogging) log.debug "Hour ${i}: condition code ${conditionCode} → intensity ${intensity}"
            }
        }
    }

    // Set the simplified attributes
    Boolean precipitationExpected = (maxIntensity > 0)
    String[] intensityTexts = ['None', 'Light', 'Moderate', 'Heavy']
    String intensityText = intensityTexts[Math.min(maxIntensity, 3)]

    sendEvent(name: 'precipitationExpected', value: precipitationExpected)
    sendEvent(name: 'precipitationIntensity', value: maxIntensity)
    sendEvent(name: 'precipitationIntensityText', value: intensityText)
    
    if (debugLogging) log.debug "Final result: expected=${precipitationExpected}, intensity=${maxIntensity} (${intensityText})"
}


