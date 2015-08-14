package com.streamdataio.android.github;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.egit.github.core.SearchRepository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.RequestException;
import org.eclipse.egit.github.core.service.RepositoryService;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    // Values from res/values/config.xml
    private int MAX_CONCURRENT_REPOS;
    private String gitHubApiToken;

    private Menu menu;
    private RepositoryService service;
    private Intent intent;
    private boolean hasResults = false;
    private ListView listView;
    private Button showButton;
    public ArrayList<String> list, selectedItems;
    SearchHistoryManager history;

    /**
     * Android activity creation handler
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        System.err.println("onCREATE callback");

        // Allow connectivity in the main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Get GitHub API token
        SharedPreferences sharedPref = getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE);
        gitHubApiToken = sharedPref.getString("gitHubApiToken", "");

        // If the user is not gitHub-authenticated --> start LoginActivity
        if (gitHubApiToken.isEmpty()) {
            startLoginActivity();
        }

        // Loading historical searches
        history = new SearchHistoryManager(this);
        MAX_CONCURRENT_REPOS = getResources().getInteger(R.integer.concurrent_repositories);

        // Getting UI Objects
        showButton = (Button) findViewById(R.id.showButton);
        listView = (ListView) findViewById(R.id.mainListView);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        hasResults = history.getSearches().size() > 0 ? true : false;

        // Display historical searches
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ArrayAdapter<String> listAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.single_repo, new ArrayList(history.getSearches()));
                listView.setAdapter(listAdapter);
            }
        });

        // ListView Item click handler
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {

                hideKeyboard();

                intent = new Intent(MainActivity.this, CommitsActivity.class);
                if (hasResults) {
                    selectedItems = new ArrayList<String>();
                    // Get multiple selected items
                    SparseBooleanArray checked = ((ListView) parent).getCheckedItemPositions();
                    for (int i = 0; i < ((ListView) parent).getAdapter().getCount(); i++) {
                        if (checked.get(i)) {
                            // Add item as a String in selectedItems Array
                            selectedItems.add(((ListView) parent).getItemAtPosition(i).toString());
                        }
                    }

                    // Put repositories-IDs available for the commits view
                    intent.putStringArrayListExtra("reposId", selectedItems);

                    // Check number of selected repositories in [1..5]
                    int numberSelectedRepos = selectedItems.size();

                    // Show commits button behavior
                    if (numberSelectedRepos > 0 && numberSelectedRepos <= MAX_CONCURRENT_REPOS) {
                        showButton.setClickable(true);
                        showButton.setText("Show commits");
                        showButton.setBackgroundResource(R.drawable.roundedbutton);
                        showButton.setVisibility(View.VISIBLE);
                    } else if (numberSelectedRepos > MAX_CONCURRENT_REPOS) {
                        showButton.setText("Too many repos");
                        showButton.setClickable(false);
                        showButton.setBackgroundResource(R.drawable.roundedbutton_disabled);
                        showButton.setVisibility(View.VISIBLE);

                    } else {
                        showButton.setClickable(false);
                        showButton.setVisibility(View.GONE);
                    }
                }
            }
        });


        // Implicit 'submit' when typing is finished
        EditText edit_txt = (EditText) findViewById(R.id.searchField);
        edit_txt.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    searchAndDisplay(null);
                    return true;
                }
                return false;
            }
        });


        // Authenticating by GitHub Java SDK
        GitHubClient client = new GitHubClient();
        client.setOAuth2Token(gitHubApiToken);
        service = new RepositoryService(client);

        // Instanciate the list of repositories ID as strings
        list = new ArrayList<>();
    }

    /**
     * Go back to LoginActivity
     */
    public void startLoginActivity() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
    }

    /**
     * Sign in button handler. Clears sharedPreferences in order not to use the public token anymore, then start LoginActivity with 'SIGN_IN' parameter.
     *
     * @param v Sign in entry in menu
     */
    public void signIn(MenuItem v) {
        SharedPreferences settings = getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE);
        settings.edit().clear().commit();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("SIGN_IN", true);
        startActivity(intent);
    }

    /**
     * Call LoginActivity with a 'SIGN_OUT' parameter
     *
     * @param v Sign out entry in menu
     */
    public void signOut(MenuItem v) {
        // Display historical searches
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                clearHistory(null);
                Intent inte = new Intent(MainActivity.this, LoginActivity.class);
                inte.putExtra("SIGN_OUT", true);
                startActivity(inte);
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Display the 'Sign-in' menu entry if we are use the public token, 'Sign-out' if the used token is different
     * @param menu
     * @return
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        System.err.println("onPrepareOptionsMenu callback");

        // If the user has chosen to use public version
        if (gitHubApiToken.equals(getResources().getString(R.string.github_public_token))) {
            MenuItem signInButton = menu.findItem(R.id.action_signin);
            signInButton.setVisible(true);

            MenuItem signOutButton = menu.findItem(R.id.action_signout);
            signOutButton.setVisible(false);
        }
        // If the user has chosen to use authenticated api
        else {
            MenuItem signInButton = menu.findItem(R.id.action_signin);
            signInButton.setVisible(false);

            MenuItem signOutButton = menu.findItem(R.id.action_signout);
            signOutButton.setVisible(true);
        }
        return true;
    }

    /**
     * Clear the main search field
     *
     * @param view
     */
    public void clearField(final View view) {
        EditText editText = (EditText) findViewById(R.id.searchField);
        editText.setText("");
    }

    /**
     * Start the CommitActivity. This procedure adds the selected repositories ID to the searches history
     */
    public void startCommitActivity(View v) {
        System.out.println(selectedItems);
        for (String item : selectedItems) {
            history.addSearch(item);
        }
        startActivity(intent);
    }


    /**
     * Clears the list of historical searches
     * @param v 'Clear History' menu entry
     */
    public void clearHistory(MenuItem v) {
        history.resetHistory();
        // Display historical searches
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ArrayAdapter<String> listAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.single_repo, new ArrayList(history.getSearches()));
                listView.setAdapter(listAdapter);
            }
        });
    }

    /**
     * Lauch the research, handle results and display them in the UI.
     * @param v
     */
    public void searchAndDisplay(final View v) {
        // Hiding Virtual Keyboard
        hideKeyboard();

        String searchSentence = ((EditText) findViewById(R.id.searchField)).getText().toString();
        // If there are no sentence to search: stopping procedure
        if (searchSentence.isEmpty())
            return;

        Log.i("info", "SEARCH '" + searchSentence + "'");

        try {
            List<SearchRepository> listRepo = service.searchRepositories(searchSentence);

            this.list.clear();
            for (SearchRepository repository : listRepo) {
                // Add a Unicode lock if the repo is private, at the beginning of the repo name
               String repo = repository.isPrivate() ? "\uD83D\uDD12" + repository.getId() : repository.getId();

                // Push the repo name to the selected repos list
                this.list.add(repo);
            }
            hasResults = !this.list.isEmpty();

            // Configure the list view
            if (!hasResults) this.list.add("No result for '" + searchSentence + "'");

            // Refresh UI
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ArrayAdapter<String> listAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.single_repo, MainActivity.this.list);
                    listView.setAdapter(listAdapter);
                }
            });

            // Display the search results
            int numResults = listRepo.size();
            String message = numResults > 99 ? "> 100" : numResults == 0 ? "No" : numResults + "";
            message += " repositories found";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        } catch (UnknownHostException e) {
            Log.i("info", "Connection fail, check the phone is connected to network");

            Toast.makeText(this, "You need to connect to the Internet", Toast.LENGTH_SHORT).show();

        } catch (RequestException e) {
            // GitHub api token may be out-of-date --> restart oauth procedure
            signIn(null);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Force the keyboard to vanish on click outside the search field
     */
    public void hideKeyboard() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Hide the virtual keyboard
                if (MainActivity.this.getCurrentFocus() != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(MainActivity.this.getCurrentFocus().getWindowToken(), 0);
                }
                showButton.setVisibility(View.GONE);
            }
        });
    }


}
