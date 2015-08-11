# Streamdata-android/github-android
This android application shows how to use the <a href="http://streamdata.io" target="_blank">streamdata.io</a> proxy in a sample app.

Streamdata.io allows to get data pushed from various sources and use them in your application.
This sample application provides GiuHub repositories commits data, pushed by Streamdata.io proxy using Server-sent events.

## License

* [Apache Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)


To run the sample, you can clone this GitHub repository, and then open the project with Android Studio.


## Add the Streamdata.io authentication token

Before running the project on a phone or emulator, you have to paste a token to be authenticated by the proxy, as well as a GitHub API token, which you can generate from the settings menu of github.com.

Modify res/values/config.xml on line 10 & 14 :

```
   <!--Streamdata.io app token, got from web portal -->
    <string name="streamdata_app_token">YOUR_STREAMDATA_APP_TOKEN</string>

    <!-- GitHub access token for public utilization
         this token allows only to read public repos-->
    <string name="github_public_token">GITHUB_API_PUBLIC_TOKEN</string>

    <!-- GitHub OAuth client public key  -->
    <string name="app_client_id">GITHUB_OAUTH_PUBLIC_KEY</string>

    <!-- GitHub OAuth client secret key  -->
    <string name="app_client_secret">GITHUB_OAUTH_PRIVATE_KEY</string>

```

To get a token, please sign up for free to the <a href="https://portal.streamdata.io/" target="_blank">streamdata.io portal</a> and follow the guidelines. You will find your token in the 'security' section.

## Project dependencies


The application dependencies are available on GitHub

* <a href="https://github.com/FasterXML/jackson-databind" target="_blank">https://github.com/FasterXML/jackson-databind</a>
* <a href="https://github.com/fge/json-patch" target="_blank">https://github.com/fge/json-patch</a>
* <a href="https://github.com/streamdataio/eventsource-android/" target="_blank">https://github.com/streamdataio/eventsource-android/</a>
* <a href="https://github.com/eclipse/egit-github" target="_blank">https://github.com/eclipse/egit-github</a>

If you have any questions or feedback, feel free to contact us at <a href="mailto://support@streamdata.io">support@streamdata.io</a>

Enjoy!
