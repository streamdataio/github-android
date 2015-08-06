package com.streamdataio.android.github;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;


/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends Activity {

    private String clientId;
    private String clientSecret;
    private String redirectUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Allow connectivity in the main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        clientId = getResources().getString(R.string.app_client_id);
        clientSecret = getResources().getString(R.string.app_client_secret);
        redirectUri = getResources().getString(R.string.redirect_uri);

        Button loginButton = (Button) findViewById(R.id.email_sign_in_button);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                System.out.println("[LoginActivity] CLICK 'Sign in' ");
                Uri uri = getIntent().getData();
                if (uri == null) {
                    attemptLogin();
                }
            }
        });


        if (getIntent().getBooleanExtra("SIGN_IN", false)) {
            attemptLogin();
        }


        Button publicVersionButton = (Button) findViewById(R.id.public_button);
        publicVersionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("[LoginActivity] CLICK 'public version' ");
                openMain(getResources().getString(R.string.github_public_token));
            }
        });

        if (getIntent().getBooleanExtra("SIGN_OUT", false)){
            System.out.println("[LoginActivity] Sign out");
            SharedPreferences settings = getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE);
            settings.edit().clear().commit();
            gitHubLogout();
        }



    }

    @Override
    protected void onResume() {
        super.onResume();

        // the intent filter defined in AndroidManifest will handle the return from ACTION_VIEW intent
        Uri uri = getIntent().getData();
        if (uri != null && uri.toString().startsWith(redirectUri)) {
            String code = uri.getQueryParameter("code");
            if (code != null) {
                System.err.println("CODE: " + code);

                try {
                    String result = downloadUrl("https://github.com/login/oauth/access_token?client_id=" + clientId + "&client_secret=" + clientSecret + "&code=" + code);
                    System.out.println(result);

                    String accessToken = result.substring(result.indexOf("=")+1, result.indexOf("&"));
                    System.out.println(accessToken);
                    openMain(accessToken);

                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else if (uri.getQueryParameter("error") != null) {
                // show an error message here
                System.err.println("ERROR: " + uri.getQueryParameter("error"));
            }
        }
    }


    public void attemptLogin() {
        SharedPreferences settings = getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE);
        settings.edit().clear().commit();
        Intent intent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/login/oauth/authorize?client_id=" + clientId + "&redirect_uri=" + redirectUri+"&scope=repo")
        );
        startActivity(intent);
    }

    public void gitHubLogout() {
        Intent intent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/logout")
        );
        startActivity(intent);
    }


    private void openMain(String accessToken) {

        SharedPreferences sharedPref = getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("gitHubApiToken", accessToken);
        editor.commit();
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    // Given a URL, establishes an HttpUrlConnection and retrieves
    // the web page content as a String, which it returns as  a string.
    private String downloadUrl(String myurl) throws IOException {
        InputStream is = null;
        try {
            HttpURLConnection conn = (HttpURLConnection) (new URL(myurl)).openConnection();
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            conn.connect();
            return convertStreamToString(conn.getInputStream());

        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}

