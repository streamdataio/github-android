package com.streamdataio.android.github;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.eclipse.egit.github.core.SearchRepository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.RepositoryService;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    // Values from res/values/config.xml
    private int MAX_CONCURRENT_REPOS;
    private String gitHubApiToken;

    private RepositoryService service;
    private Intent intent;
    private boolean hasResults = false;
    private ListView listView;
    private Button showButton;
    public ArrayList<String> liste, selectedItems;
    SearchHistoryManager history;


    /**
     * Android activity creation handler
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Allow connectivity in the main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Loading historical searches
        history = new SearchHistoryManager(this);

        MAX_CONCURRENT_REPOS = getResources().getInteger(R.integer.concurrent_repositories);
                gitHubApiToken = String.valueOf(getResources().getText(R.string.github_api_token));

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

        // Authenticating by GitHub Java SDK
        GitHubClient client = new GitHubClient();
        client.setOAuth2Token(gitHubApiToken);
        service = new RepositoryService(client);

        // Instanciate the list of repositories ID as strings
        liste = new ArrayList<>();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // Clears search text field on focussing it
    public void clearField(View v) {
        EditText editText = (EditText) findViewById(R.id.searchField);
        editText.setText("");
    }

    // Add selected items to history, then start CommitActivity
    public void startCommitActivity(View v) {
        System.out.println(selectedItems);
        for (String item : selectedItems) {
            history.addSearch(item);
        }
        startActivity(intent);
    }

    // Clears the list of historical searches
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

    public void searchAndDisplay(final View v) {

        // Hiding Virtual Keyboard
        hideKeyboard();

        String searchSentense = ((EditText) findViewById(R.id.searchField)).getText().toString();
        // If there are no sentense to search: stopping procedure
        if (searchSentense.isEmpty())
            return;

        Log.i("info", "SEARCH '" + searchSentense + "'");

        try {
            List<SearchRepository> list = service.searchRepositories(searchSentense);

            liste.clear();
            for (SearchRepository repository : list) {
                if (!repository.isPrivate()) {
                    liste.add(repository.getId());
                }
            }
            hasResults = !liste.isEmpty();

            // Configure the list view
            if (!hasResults) liste.add("No result for '" + searchSentense + "'");

            // Refresh UI
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ArrayAdapter<String> listAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.single_repo, liste);
                    listView.setAdapter(listAdapter);
                }
            });

            // Display the search results
            int numResults = list.size();
            String message = numResults > 99 ? "> 100" : numResults == 0 ? "No" : numResults + "";
            message += " repositories found";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        } catch (UnknownHostException e) {
            Log.i("info", "Connection fail, check the phone is connected to network");

            Toast.makeText(this, "You need to connect to the Internet", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
