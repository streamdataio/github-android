package com.streamdataio.android.github;

import android.content.Context;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by Streamdata.io on 09/07/15.
 */
public class SearchHistoryManager {

    private static Context context;

    private Set<String> searchList;
    private String filename ;
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs a SearchHistoryManager, then call loadData() method. It requires an Activity Context.
     * @param c a Context from an Activity
     */
    public SearchHistoryManager(Context c) {
        this.context = c;
        filename = String.valueOf(context.getResources().getText(R.string.history_filename));
        loadData();
    }

    /**
     * Loads repositories ID as String from the JSON file to a List of String
     */
    private void loadData() {
        try {
            if (!fileExists(filename)) {
                resetHistory();
            }
            searchList = mapper.readValue(context.openFileInput(filename), Set.class);

        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(searchList);
    }

    /**
     * Clears the searches history. Reset the file and the cached List of Strings
     */
    public void resetHistory() {
        List<String> list = new ArrayList<String>();
        list.clear();
        try {
            mapper.writeValue(context.openFileOutput(filename, Context.MODE_WORLD_WRITEABLE), list);
            if (searchList != null)
                searchList.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add an entry of search visited by user. Appends it at the end of the JSON file.
     * @param s Repository ID as a String
     */
    public void addSearch(String s) {
        try {
                searchList.add(s);
                mapper.writeValue(context.openFileOutput(filename, Context.MODE_WORLD_WRITEABLE), searchList);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Return all the history searches as a List of Strings
     * @return
     */
    public Set<String> getSearches() {
        return searchList;
    }


    /**
     * Return whether a file exits in the context path
     * @param fname path to the file
     * @return true if the file exits, else return false.
     */
    private boolean fileExists(String fname) {
        File file = context.getFileStreamPath(fname);
        return file.exists();
    }
}




