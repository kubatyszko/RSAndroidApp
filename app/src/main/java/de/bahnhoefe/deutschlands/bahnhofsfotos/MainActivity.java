package de.bahnhoefe.deutschlands.bahnhofsfotos;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import de.bahnhoefe.deutschlands.bahnhofsfotos.Dialogs.AppInfoFragment;
import de.bahnhoefe.deutschlands.bahnhofsfotos.db.BahnhofsDbAdapter;
import de.bahnhoefe.deutschlands.bahnhofsfotos.db.CustomAdapter;
import de.bahnhoefe.deutschlands.bahnhofsfotos.model.Bahnhof;
import de.bahnhoefe.deutschlands.bahnhofsfotos.util.Constants;

import static com.google.android.gms.analytics.internal.zzy.d;
import static java.lang.Integer.getInteger;
import static java.lang.Integer.parseInt;
import static java.lang.Integer.valueOf;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String DIALOG_TAG = "App Info Dialog";
    public final String TAG = "Bahnhoefe";
    private TextView tvDownload;
    private BahnhofsDbAdapter dbAdapter;
    private Context context;
    private TextView tvUpdate;
    private String lastUpdateDate;
    private static final int PERMISSION_REQUEST_CODE = 200;
    private NavigationView navigationView;
    File file;
    private int count=0;

    CustomAdapter customAdapter;
    ListView listView;
    Cursor cursor;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        dbAdapter = new BahnhofsDbAdapter(this);
        dbAdapter.open();


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Will be implemented later.", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();


        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        handleGalleryNavItem();

        View header = navigationView.getHeaderView(0);
        TextView tvUpdate = (TextView) header.findViewById(R.id.tvUpdate);


        try {
            lastUpdateDate = loadUpdateDateFromFile("updatedate.txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!lastUpdateDate.equals("")) {
            tvUpdate.setText("Letzte Aktualisierung am: " + lastUpdateDate);
        } else {
            disableNavItem();
            tvUpdate.setText(R.string.no_stations_in_database);
        }

        cursor = dbAdapter.getStationsList();
        customAdapter = new CustomAdapter(this, cursor,0);
        listView = (ListView) findViewById(R.id.lstStations);
        listView.setAdapter(customAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> listview, View view, int position, long id) {

                Bahnhof bahnhof = dbAdapter.fetchBahnhof(id);
                LatLng bhfposition = new LatLng(bahnhof.getLat(),bahnhof.getLon());
                String bahnhofNr = String.valueOf(bahnhof.getId());
                Class cls = DetailsActivity.class;
                Intent intentDetails = new Intent(MainActivity.this, cls);
                intentDetails.putExtra("bahnhofName",bahnhof.getTitle());
                intentDetails.putExtra("bahnhofNr",bahnhofNr);
                intentDetails.putExtra("position",bhfposition);
                startActivity(intentDetails);

            }
        });



        Intent searchIntent = getIntent();
        if(Intent.ACTION_SEARCH.equals(searchIntent.getAction())){
            String query = searchIntent.getStringExtra(SearchManager.QUERY);
            Toast.makeText(MainActivity.this,query,Toast.LENGTH_SHORT).show();
        }


    }


    private void handleGalleryNavItem() {

        file = new File(Environment.getExternalStorageDirectory()
                + File.separator + "Bahnhofsfotos");
        Log.d(TAG, file.toString());

        Menu menuNav=navigationView.getMenu();
        MenuItem nav_itemGallery = menuNav.findItem(R.id.nav_your_own_station_photos);

        if (file.isDirectory()) {
            String[] files = file.list();
            if (files == null) {
                //directory is empty
                nav_itemGallery.setEnabled(false);
            }else{
                nav_itemGallery.setEnabled(true);
            }
        }else{
            nav_itemGallery.setEnabled(false);
        }

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem item = menu.findItem(R.id.search);


        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            SearchManager manager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            SearchView search = (SearchView) menu.findItem(R.id.search).getActionView();
            search.setSearchableInfo(manager.getSearchableInfo(getComponentName()));

            search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

                @Override
                public boolean onQueryTextSubmit(String s) {
                    Log.d(TAG, "onQueryTextSubmit ");
                    cursor = dbAdapter.getBahnhofsListByKeyword(s);
                    if (cursor == null){
                        Toast.makeText(MainActivity.this,"No records found!",Toast.LENGTH_LONG).show();
                    }else{
                        Toast.makeText(MainActivity.this, cursor.getCount() + " records found!",Toast.LENGTH_LONG).show();
                    }
                    customAdapter.swapCursor(cursor);

                    return false;
                }

                @Override
                public boolean onQueryTextChange(String s) {
                    Log.d(TAG, "onQueryTextChange ");
                    cursor = dbAdapter.getBahnhofsListByKeyword(s);
                    if (cursor!=null){
                        customAdapter.swapCursor(cursor);
                    }
                    return false;
                }

            });

        }

        return true;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_your_data) {
            Intent intent = new Intent(de.bahnhoefe.deutschlands.bahnhofsfotos.MainActivity.this, MyDataActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_update_photos) {
            new JSONTask().execute(Constants.BAHNHOEFE_OHNE_PHOTO_URL);
            enableNavItem();
        } else if (id == R.id.nav_your_own_station_photos) {
            Intent intent = new Intent(de.bahnhoefe.deutschlands.bahnhofsfotos.MainActivity.this, GalleryActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_stations_without_photo) {
            Intent intent = new Intent(de.bahnhoefe.deutschlands.bahnhofsfotos.MainActivity.this, MapsAcitivity.class);
            startActivity(intent);
        }else if (id == R.id.nav_all_stations_without_photo) {
            Intent intent = new Intent(de.bahnhoefe.deutschlands.bahnhofsfotos.MainActivity.this, MapsAllAcitivity.class);
            startActivity(intent);

        }else if (id == R.id.nav_app_info) {
            AppInfoFragment appInfoFragment = new AppInfoFragment();
            appInfoFragment.show(getSupportFragmentManager(),DIALOG_TAG);

        } /*else if (id == R.id.nav_send) {

        }*/

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void enableNavItem() {
        // after loading stations enable menu to start MapsActivity
        Menu menuNav=navigationView.getMenu();
        MenuItem nav_item2 = menuNav.findItem(R.id.nav_stations_without_photo);
        MenuItem nav_item3 = menuNav.findItem(R.id.nav_all_stations_without_photo);
        nav_item3.setEnabled(true);
        nav_item2.setEnabled(true);
    }

    private void disableNavItem(){
        // if there are no stations available, disable the menu to start MapsActivity
        Menu menuNav = navigationView.getMenu();
        MenuItem nav_item2 = menuNav.findItem(R.id.nav_stations_without_photo);
        MenuItem nav_item3 = menuNav.findItem(R.id.nav_all_stations_without_photo);
        nav_item3.setEnabled(false);
        nav_item2.setEnabled(false);
    }

    public class JSONTask extends AsyncTask<String, String, List<Bahnhof>>{

        private ProgressDialog progressDialog;


        @Override
        protected List<Bahnhof> doInBackground(String... params) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            Date date = new Date();
            long aktuellesDatum = date.getTime();
            List<Bahnhof> bahnhoefe = new ArrayList<Bahnhof>();

            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection)url.openConnection();
                connection.connect();
                InputStream stream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(stream));
                StringBuffer buffer = new StringBuffer();
                String line = "";
                while((line = reader.readLine()) != null){
                    buffer.append(line);
                }
                String finalJson =  buffer.toString();

                try {
                    JSONArray bahnhofList = new JSONArray(finalJson);
                    count = bahnhofList.length();

                    for (int i = 0; i < bahnhofList.length(); i++){
                        JSONObject jsonObj = (JSONObject) bahnhofList.get(i);
                        publishProgress(((i+1) + " von " + bahnhofList.length()));

                        String title = jsonObj.getString(Constants.DB_JSON_CONSTANTS.KEY_TITLE);
                        String id = jsonObj.getString(Constants.DB_JSON_CONSTANTS.KEY_ID);
                        String lat = jsonObj.getString(Constants.DB_JSON_CONSTANTS.KEY_LAT);
                        String lon = jsonObj.getString(Constants.DB_JSON_CONSTANTS.KEY_LON);

                        Bahnhof bahnhof = new Bahnhof();
                        bahnhof.setTitle(title);
                        bahnhof.setId(parseInt(id));
                        bahnhof.setLat(Float.parseFloat(lat));
                        bahnhof.setLon(Float.parseFloat(lon));
                        bahnhof.setDatum(aktuellesDatum);


                        bahnhoefe.add(bahnhof);
                        //Log.d("DatenbankInsertOk ...", bahnhof.toString());
                    }

                    dbAdapter.insertBahnhoefe(bahnhoefe);
                    publishProgress("Datenbank aktualisiert");
                    return bahnhoefe;

                } catch (JSONException e) {
                    e.printStackTrace();
                }


            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }finally{
                if(connection != null){
                    connection.disconnect();
                }
                try {
                    if(reader != null){
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<Bahnhof> result) {
            progressDialog.dismiss();
            writeUpdateDateInFile();
            tvUpdate = (TextView) findViewById(R.id.tvUpdate);
            try {
                tvUpdate.setText("Letzte Aktualisierung am: " + loadUpdateDateFromFile("updatedate.txt") );
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.GINGERBREAD){
                customAdapter.swapCursor(dbAdapter.getStationsList());
            } else {
                customAdapter.changeCursor(dbAdapter.getStationsList());
            }

            unlockScreenOrientation();

        }

        @Override
        protected void onPreExecute() {
            lockScreenOrientation();
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setIndeterminate(false);

        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            //progressDialog.setMessage("Lade Daten ... " + values[0] + count);
            progressDialog.setMessage("Lade Daten von " + count + " Bahnhöfen. \nDas dauert ein bisschen");

            // show it
            progressDialog.show();

        }

        private void lockScreenOrientation() {
            int currentOrientation = getResources().getConfiguration().orientation;
            if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }

        private void unlockScreenOrientation() {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }
    }

    public BahnhofsDbAdapter getDbHelper(){
        return dbAdapter;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbAdapter !=null){
            dbAdapter.close();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    public void onResume() {
        super.onResume();
        handleGalleryNavItem();
    }
    private void writeUpdateDateInFile() {

        try {
            Calendar c = Calendar.getInstance();
            SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            final String lastUpdateDate = df.format(c.getTime());
            FileOutputStream updateDate = openFileOutput("updatedate.txt",MODE_PRIVATE);
            OutputStreamWriter osw = new OutputStreamWriter(updateDate);
            try {
                osw.write(lastUpdateDate);
                osw.flush();
                osw.close();
                Toast.makeText(getBaseContext(),"Aktualisierungsdatum gespeichert", Toast.LENGTH_LONG).show();
            } catch (IOException ioe) {
                Log.e(TAG, ioe.toString());
            }
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG,fnfe.toString());
        }

    }

    public String loadUpdateDateFromFile(String filename) throws Exception{
        String retString = "";
        BufferedReader reader = null;
        try{
            FileInputStream in = this.openFileInput(filename);
            reader = new BufferedReader(new InputStreamReader(in));
            String zeile;
            while ((zeile = reader.readLine()) != null){
                retString += zeile;
            }
            reader.close();

        }catch (FileNotFoundException fnfe){
            Log.e(TAG,fnfe.toString());
        }


        return retString;
    }



}
