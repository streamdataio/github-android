package com.streamdataio.android.github;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import tylerjroach.com.eventsource_android.EventSource;
import tylerjroach.com.eventsource_android.EventSourceHandler;
import tylerjroach.com.eventsource_android.MessageEvent;

/**
 * This activity allows to listen to commits of public repositories got from GitHub API, using Streamdata.io proxy
 *
 * @author Streamdata.io - July 2015
 */
public class CommitsActivity extends Activity {

    private final ObjectMapper mapper = new ObjectMapper();

    // Global List of Commits
    private ArrayList<Commit> commits = new ArrayList<Commit>();

    // List of repositories identifiers
    private ArrayList<String> reposIdArray = new ArrayList<>();

    // Map of colors foreach repo: (repositoriesID, color)
    private HashMap<String, Integer> repoColors = new HashMap<>();

    // Map of EventSource foreach repo: (repositoriesID, EventSource)
    private ConcurrentHashMap<String, EventSource> reposEventSources = new ConcurrentHashMap<>();

    private ListView listView;
    private MyListAdapter adapter;

    private String streamdataioProxyPrefix;
    private String streamdataioAppToken;
    private String gitHubApiToken;
    private String myApi;


    /* ************** Android Activity states handlers **************** */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_commits);
        Intent intent = getIntent();

        // Get GitHub API token from SharedPreferences
        SharedPreferences sharedPref = getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE);
        String defaultGithubToken = "";
        gitHubApiToken = sharedPref.getString("gitHubApiToken", defaultGithubToken);

        // If the user is not gitHub-authenticated --> start LoginActivity
        if (gitHubApiToken.isEmpty()) {
            startLoginActivity();
        }

        // Get the array of Repositories-ID as Strings
        reposIdArray = intent.getStringArrayListExtra("reposId");
        //gitHubApiToken = intent.getStringExtra("gitHubApiToken");

        System.out.println("[CommitsActivity] gitHubApiToken = '" + gitHubApiToken + "'");

        // Generate a random color & eventsource foreach repository
        for (int i = 0; i < reposIdArray.size(); i++) {
            repoColors.put(reposIdArray.get(i), randomColor());
        }

        // Getting configuration values from res/values/config.xml
        streamdataioAppToken = String.valueOf(getResources().getText(R.string.streamdata_app_token));
        streamdataioProxyPrefix = String.valueOf(getResources().getText(R.string.streamdata_proxy_prefix));
        //gitHubApiToken = String.valueOf(getResources().getText(R.string.github_api_token));


        listView = (ListView) findViewById(R.id.listView);
        adapter = new MyListAdapter(this, commits);
        listView.setAdapter(adapter);

        // Instanciate an empty array of commits
        commits = new ArrayList<Commit>();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Connection to every EventSources
        connectAll();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Disconnection from every EventSources
        disconnectAll();
    }

/* ***************** Connection and deconnection methods ***************** */

    /**
     * Create the EventSource object & start listening SSE incoming messages
     */
    private void connect(String api) {

        //remove the private-repo-lock character if exists
        String clearApi = api.startsWith("\uD83D\uDD12") ? api.substring(2) :api;

        // Add the GitHub API token with an URL parameter (only way to authenticate)
        //myApi = "https://api.github.com/repos/" + api + "/commits?X-Sd-Token="+streamdataioAppToken+"&access_token=" + gitHubApiToken;
        myApi = "https://api.github.com/repos/" + clearApi + "/commits?access_token=" + gitHubApiToken;

        // Add the Streamdata.io authentication token
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-Sd-Token", streamdataioAppToken);

        // Create the EventSource with API URL & Streamdata.io authentication token
        try {
            SSEHandler sseHandler = new SSEHandler(api);
            EventSource eventSource = new EventSource(new URI(streamdataioProxyPrefix), new URI(myApi), sseHandler, headers);

            reposEventSources.put(api, eventSource);

            // Start data receiving from API
            eventSource.connect();

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    /**
     * Start listening to every selected repositories commits
     */
    private void connectAll() {
        for (String repo : reposIdArray) {
            Log.i("info", "Connecting : " + repo);
            connect(repo);
        }
    }

    /**
     * Closes the event source connection and dereference the EventSource object
     */
    private void disconnect(String api) {
        // Disconnect the eventSource Handler
        if (reposEventSources.containsKey(api)) {
            if (reposEventSources.get(api).isConnected()) {
                reposEventSources.get(api).close();
                reposEventSources.remove(api);
            }
        }
    }

    /**
     * Closes every open EventSources
     */
    private void disconnectAll() {
        for (Map.Entry<String, EventSource> entry : reposEventSources.entrySet()) {
            disconnect(entry.getKey());
            Log.i("info", "Disconnecting : " + entry.getKey());

        }
    }

    /* ********************** CLASS SSEHandler ********************* */
    private class SSEHandler implements EventSourceHandler {

        private JsonNode ownData;
        private String repoName;

        public SSEHandler() {
        }

        public SSEHandler(String repoName) {
            this();
            this.repoName = repoName;
        }

        /**
         * SSE handler for connection starting
         */
        @Override
        public void onConnect() {
            System.out.println("SSE Connected");
        }

        /**
         * SSE incoming message handler
         *
         * @param event   type of message
         * @param message message JSON content
         * @throws IOException if JSON syntax is not valid
         */
        @Override
        public void onMessage(String event, MessageEvent message) throws IOException {

            if ("data".equals(event)) {
                // SSE message is a snapshot
                ownData = mapper.readTree(message.data);

                // Update commits array
                updateCommits();

            } else if ("patch".equals(event)) {
                // SSE message is a patch
                try {
                    JsonNode patchNode = mapper.readTree(message.data);
                    JsonPatch patch = JsonPatch.fromJson(patchNode);
                    ownData = patch.apply(ownData);

                    // Get the concerned repository name
                    String commitMessage = ownData.get(0).path("commit").path("message").textValue();

                    // Spawning an Android notification
                    createNotification("New Commit: " + repoName.substring(repoName.indexOf("/") + 1), commitMessage);

                    // Update commits array
                    updateCommits();
                } catch (JsonPatchException e) {
                    e.printStackTrace();
                }
            } else {
                Log.d("Debug", "Disconnecting : " + message.toString());

                if (message.data.contains("HTTP/1.1 401 Unauthorized")) {
                    // GitHub api token may be out-of-date --> restart oauth procedure
                    SharedPreferences settings = getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE);
                    settings.edit().clear().commit();
                    startLoginActivity();
                }
                throw new RuntimeException("Wrong SSE message!");
            }
        }

        /**
         * Create an Android Notification with the given title et content
         * @param title notification title
         * @param text notification content text
         */
        private final void createNotification(String title, String text) {

            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(CommitsActivity.this)
                            .setSmallIcon(R.drawable.icon)
                            .setContentTitle(title)
                            .setContentText(text);

            // Sets an ID for the notification
            Random rand = new Random();
            int mNotificationId = rand.nextInt(51);

            // Gets an instance of the NotificationManager service
            NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            // Builds the notification and issues it.
            mNotifyMgr.notify(mNotificationId, mBuilder.build());
        }

        /**
         * SSE error Handler. If the device is not connected to the Internet, all eventsources will be interrupted
         */
        @Override
        public void onError(Throwable t) {
            // Network error message...
            if (t.toString().contains("java.nio.channels.UnresolvedAddressException")) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(CommitsActivity.this, "You need to connect to the Internet", Toast.LENGTH_SHORT).show();
                    }
                });
                CommitsActivity.this.disconnectAll();
            }
        }

        /**
         * SSE Handler for connection interruption
         */
        @Override
        public void onClosed(boolean willReconnect) {
            System.out.println("SSE Closed - reconnect? " + willReconnect);
        }

        /**
         * Return commits of ONE repository, depending on its EventSource, as a JsonNode
         *
         * @return
         */
        public JsonNode getOwnData() {
            return ownData;
        }
    }

    /**
     * Update the cached commits array. This method should be called on snapshot or patch event.
     */
    public void updateCommits() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        // Clear commits
        commits.clear();

        Commit commit = null;

        // foreach EventSource Handler ...
        for (Map.Entry<String, EventSource> entry : reposEventSources.entrySet()) {
            SSEHandler sseHandler = (SSEHandler) entry.getValue().getEventSourceHandler();
            JsonNode data = sseHandler.getOwnData();

            // Reconstructs commits array from JSON data
            for (Iterator<JsonNode> iterator = data.iterator(); iterator.hasNext(); ) {
                JsonNode commitJson = iterator.next();
                try {
                    sdf.parse(commitJson.path("commit").path("author").path("date").textValue());

                    commit = new Commit(
                            sdf.parse(commitJson.path("commit").path("author").path("date").textValue()),
                            commitJson.path("commit").path("author").path("name").textValue(),
                            commitJson.path("commit").path("message").textValue(),
                            commitJson.path("sha").textValue().substring(0, 9),
                            entry.getKey()
                    );

                } catch (ParseException e) {
                    e.printStackTrace();
                }
                //commit.print();
                commits.add(commit);
            }
        }
        Collections.sort(commits, new DateComparator());

        // Refresh UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.updateData(commits);
                adapter.notifyDataSetChanged();
            }
        });
    }

    /**
     * Draw a random color in [#000000 .. #E6E6E6]. (about 12 millions outcomes).
     * Used as text background, the lightest configuration render a white text still readable
     * @return
     */
    public int randomColor() {
        Random rnd = new Random();
        return Color.argb(255, rnd.nextInt(230), rnd.nextInt(230), rnd.nextInt(230));
    }

    /* ********************************** Class Commit ********************************** */
    private class Commit {
        public Date date;
        public String user;
        public String comment;
        public String uid;
        public String repositoryID;

        public Commit(Date date, String user, String comment, String uid, String repositoryID) {
            this.date = date;
            this.user = user;
            this.comment = comment;
            this.uid = uid;
            this.repositoryID = repositoryID;
        }
    }

    /* ********************************** Class MyListAdapter ********************************** */

    private class MyListAdapter extends BaseAdapter {

        private final LayoutInflater inflater;
        private ArrayList<Commit> commits;

        /**
         * Constructor for this adapter, we'll accept all the things we need here
         *
         * @param commits
         */
        public MyListAdapter(final Context context, final ArrayList<Commit> commits) {
            this.commits = commits;
            inflater = LayoutInflater.from(context);
        }

        public void updateData(ArrayList<Commit> commits) {
            this.commits = new ArrayList<Commit>(commits);
        }

        public ArrayList<Commit> getData() {
            return commits;
        }

        @Override
        public int getCount() {
            return commits != null ? commits.size() : 0;
        }

        @Override
        public Object getItem(int i) {
            return commits != null ? commits.get(i) : null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewWrapper viewWrapper;
            DateFormat mediumDateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, new Locale("EN", "en"));

            if (convertView == null) {
                // Store view elements in the Tag to improve list performance
                viewWrapper = new ViewWrapper();
                convertView = inflater.inflate(R.layout.single_commit, null);
                viewWrapper.uid = (TextView) convertView.findViewById(R.id.commit_id);
                viewWrapper.title = (TextView) convertView.findViewById(R.id.commit_message);
                viewWrapper.user = (TextView) convertView.findViewById(R.id.commit_author);
                viewWrapper.date = (TextView) convertView.findViewById(R.id.commit_date);
                viewWrapper.repoID = (TextView) convertView.findViewById(R.id.commit_repoid);
                convertView.setTag(viewWrapper);

            } else {
                // we've just avoided calling findViewById() on resource every time just use the viewHolder instead
                viewWrapper = (ViewWrapper) convertView.getTag();
            }

            // assign values if the object is not null
            if (commits != null) {
                // get the TextView from the ViewHolder and then set the text
                viewWrapper.uid.setText(commits.get(position).uid);
                viewWrapper.date.setText(mediumDateFormat.format(commits.get(position).date));
                viewWrapper.title.setText(commits.get(position).comment);
                viewWrapper.user.setText("by " + commits.get(position).user);
                viewWrapper.repoID.setText(commits.get(position).repositoryID);
                viewWrapper.repoID.setBackgroundColor(repoColors.get(commits.get(position).repositoryID));
            }

            if (position % 2 == 1) {
                convertView.setBackgroundColor(Color.rgb(230, 235, 255));
            } else {
                convertView.setBackgroundColor(Color.rgb(255, 255, 255));
            }
            return convertView;
        }
    }

    /* ********************************** Class ViewWrapper ********************************** */

    private static class ViewWrapper {
        TextView uid;
        TextView title;
        TextView user;
        TextView date;
        TextView repoID;
    }
    /* ********************************** Class DateComparator ********************************** */

    public class DateComparator implements Comparator<Commit> {
        @Override
        public int compare(Commit o1, Commit o2) {
            return o2.date.compareTo(o1.date);
        }
    }

    private void startLoginActivity() {
        Intent inte = new Intent(this, LoginActivity.class);
        startActivity(inte);
    }
}
