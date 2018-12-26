## Running examples locally

With the first Gradle invocation, Gradle will take the value of the `MAPBOX_ACCESS_TOKEN` environment variable and save it to `/src/main/res/values/developer-config.xml`. If the environment variable wasn't set, you can create/edit the `developer-config.xml` file. Create an access_token String resource and paste your access token into it:

```xml
<string name="access_token">PASTE_YOUR_TOKEN_HERE</string>
```