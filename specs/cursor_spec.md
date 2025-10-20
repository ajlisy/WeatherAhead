# Spec for Cursor to generate code

Call this project "WeatherAhead".

I am writing a groovy script to use on a hubitat home automation system to get future weather from OpenWeatherMap. You can find API documentation for the OneCall API at https://openweathermap.org/api/one-call-3. Any implementation should read the API documentation carefully to ensure that the build is using the API correctly.

I want the following functionality:
- The script is a driver, and will allow me to create virtual devices in Hubitat that poll the OpenWeatherMap API to retrieve values
 - The 'WeatherAhead Driver' device that exposes whether precipitation will occur in the next 8 hours, as well as the intensity of the precipitation over the next 8 hours.

The driver should also take the following fields:
- OpenWeatherMap API key -- this is used to hit the API per the spec at the OneCall API documentation listed above found at https://openweathermap.org/api/
- Latitude (optional for input, if not provided default to Hubitat's latitude value)
- Longitude (optional for input, if not provided default to Hubitat's longitude value)
- units (optional, if not provided default to Hubitat's temp scale value)