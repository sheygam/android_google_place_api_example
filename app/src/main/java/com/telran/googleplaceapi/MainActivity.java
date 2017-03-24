package com.telran.googleplaceapi;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.location.places.GeoDataApi;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import android.content.Context;
import android.graphics.Typeface;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.data.DataBufferUtils;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.location.places.AutocompletePredictionBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    protected GoogleApiClient mGoogleApiClient;

    private PlaceAutocompleteAdapter mAdapter;

    private AutoCompleteTextView mAutocompleteView;


    private static final LatLngBounds BOUNDS_GREATER_SYDNEY = new LatLngBounds(
            new LatLng(-34.041458, 150.790100), new LatLng(-33.682247, 151.383362));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Construct a GoogleApiClient for the {@link Places#GEO_DATA_API} using AutoManage
        // functionality, which automatically sets up the API client to handle Activity lifecycle
        // events. If your activity does not extend FragmentActivity, make sure to call connect()
        // and disconnect() explicitly.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, 0 /* clientId */, this)
                .addApi(Places.GEO_DATA_API)
                .build();

        // Retrieve the AutoCompleteTextView that will display Place suggestions.
        mAutocompleteView = (AutoCompleteTextView) findViewById(R.id.autocomplete_places);

        // Register a listener that receives callbacks when a suggestion has been selected
        mAutocompleteView.setOnItemClickListener(mAutocompleteClickListener);
        mAutocompleteView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                mAutocompleteView.setSelection(0);
            }
        });

        // Set up the adapter that will retrieve suggestions from the Places Geo Data API that cover
        // the entire world.
        mAdapter = new PlaceAutocompleteAdapter(this, mGoogleApiClient, BOUNDS_GREATER_SYDNEY, null);
        mAutocompleteView.setAdapter(mAdapter);
    }

    /**
     * Listener that handles selections from suggestions from the AutoCompleteTextView that
     * displays Place suggestions.
     * Gets the place id of the selected item and issues a request to the Places Geo Data API
     * to retrieve more details about the place.
     *
     * @see GeoDataApi#getPlaceById(GoogleApiClient,
     * String...)
     */
    private AdapterView.OnItemClickListener mAutocompleteClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            /*
             Retrieve the place ID of the selected item from the Adapter.
             The adapter stores each Place suggestion in a AutocompletePrediction from which we
             read the place ID and title.
              */
            final AutocompletePrediction item = mAdapter.getItem(position);
            final String placeId = item.getPlaceId();
            final CharSequence primaryText = item.getPrimaryText(null);
            /*
             Issue a request to the Places Geo Data API to retrieve a Place object with additional
             details about the place.
              */
            PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi.getPlaceById(mGoogleApiClient, placeId);
            placeResult.setResultCallback(mUpdatePlaceDetailsCallback);

            Toast.makeText(getApplicationContext(), "Clicked: " + primaryText, Toast.LENGTH_SHORT).show();

        }
    };

    /**
     * Callback for results from a Places Geo Data API query that shows the first place result in
     * the details view on screen.
     */
    private ResultCallback<PlaceBuffer> mUpdatePlaceDetailsCallback = new ResultCallback<PlaceBuffer>() {
        String placeId = "";
        @Override
        public void onResult(PlaceBuffer places) {
            if (!places.getStatus().isSuccess()) {
                // Request did not complete successfully
                places.release();
                return;
            }

            final Place place = places.get(0);
            Log.d("PlaceId:", String.valueOf(place.getPlaceTypes().get(0)));
            placeId = String.valueOf(place.getPlaceTypes().get(0));

        }
    };

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // TODO(Developer): Check error code and notify the user of error state and resolution.
        Toast.makeText(this,
                "Could not connect to Google API Client: Error " + connectionResult.getErrorCode(),
                Toast.LENGTH_SHORT).show();
    }




    /**
     * Adapter that handles Autocomplete requests from the Places Geo Data API.
     * {@link AutocompletePrediction} results from the API are frozen and stored directly in this
     * adapter. (See {@link AutocompletePrediction#freeze()}.)
     * <p>
     * Note that this adapter requires a valid {@link com.google.android.gms.common.api.GoogleApiClient}.
     * The API client must be maintained in the encapsulating Activity, including all lifecycle and
     * connection states. The API client must be connected with the {@link Places#GEO_DATA_API} API.
     */
    public static class PlaceAutocompleteAdapter
            extends ArrayAdapter<AutocompletePrediction> implements Filterable {

        private static final String TAG = "PlaceAutocompleteAdapter";
        private static final CharacterStyle STYLE_BOLD = new StyleSpan(Typeface.BOLD);
        /**
         * Current results returned by this adapter.
         */
        private ArrayList<AutocompletePrediction> mResultList;

        /**
         * Handles autocomplete requests.
         */
        private GoogleApiClient mGoogleApiClient;

        /**
         * The bounds used for Places Geo Data autocomplete API requests.
         */
        private LatLngBounds mBounds;

        /**
         * The autocomplete filter used to restrict queries to a specific set of place types.
         */
        private AutocompleteFilter mPlaceFilter;

        /**
         * Initializes with a resource for text rows and autocomplete query bounds.
         *
         * @see android.widget.ArrayAdapter#ArrayAdapter(android.content.Context, int)
         */
        public PlaceAutocompleteAdapter(Context context, GoogleApiClient googleApiClient,
                                        LatLngBounds bounds, AutocompleteFilter filter) {
            super(context, android.R.layout.simple_expandable_list_item_2, android.R.id.text1);
            mGoogleApiClient = googleApiClient;
            mBounds = bounds;
            mPlaceFilter = filter;
        }

        /**
         * Sets the bounds for all subsequent queries.
         */
        public void setBounds(LatLngBounds bounds) {
            mBounds = bounds;
        }

        /**
         * Returns the number of results received in the last autocomplete query.
         */
        @Override
        public int getCount() {
            return mResultList.size();
        }

        /**
         * Returns an item from the last autocomplete query.
         */
        @Override
        public AutocompletePrediction getItem(int position) {
            return mResultList.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = super.getView(position, convertView, parent);

            // Sets the primary and secondary text for a row.
            // Note that getPrimaryText() and getSecondaryText() return a CharSequence that may contain
            // styling based on the given CharacterStyle.

            AutocompletePrediction item = getItem(position);

            TextView textView1 = (TextView) row.findViewById(android.R.id.text1);
            TextView textView2 = (TextView) row.findViewById(android.R.id.text2);
            textView1.setText(item.getPrimaryText(STYLE_BOLD));
            textView2.setText(item.getSecondaryText(STYLE_BOLD));

            return row;
        }

        /**
         * Returns the filter for the current set of autocomplete results.
         */
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();

                    // We need a separate list to store the results, since
                    // this is run asynchronously.
                    ArrayList<AutocompletePrediction> filterData = new ArrayList<>();

                    // Skip the autocomplete query if no constraints are given.
                    if (constraint != null) {
                        // Query the autocomplete API for the (constraint) search string.
                        filterData = getAutocomplete(constraint);
                    }

                    results.values = filterData;
                    if (filterData != null) {
                        results.count = filterData.size();
                    } else {
                        results.count = 0;
                    }

                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {

                    if (results != null && results.count > 0) {
                        // The API returned at least one result, update the data.
                        mResultList = (ArrayList<AutocompletePrediction>) results.values;
                        notifyDataSetChanged();
                    } else {
                        // The API did not return any results, invalidate the data set.
                        notifyDataSetInvalidated();
                    }
                }

                @Override
                public CharSequence convertResultToString(Object resultValue) {
                    // Override this method to display a readable result in the AutocompleteTextView
                    // when clicked.
                    if (resultValue instanceof AutocompletePrediction) {
                        return ((AutocompletePrediction) resultValue).getFullText(null);
                    } else {
                        return super.convertResultToString(resultValue);
                    }
                }
            };
        }

        /**
         * Submits an autocomplete query to the Places Geo Data Autocomplete API.
         * Results are returned as frozen AutocompletePrediction objects, ready to be cached.
         * objects to store the Place ID and description that the API returns.
         * Returns an empty list if no results were found.
         * Returns null if the API client is not available or the query did not complete
         * successfully.
         * This method MUST be called off the main UI thread, as it will block until data is returned
         * from the API, which may include a network request.
         *
         * @param constraint Autocomplete query string
         * @return Results from the autocomplete API or null if the query was not successful.
         * @see Places#GEO_DATA_API#getAutocomplete(CharSequence)
         * @see AutocompletePrediction#freeze()
         */
        private ArrayList<AutocompletePrediction> getAutocomplete(CharSequence constraint) {
            if (mGoogleApiClient.isConnected()) {
                //Log.i(TAG, "Starting autocomplete query for: " + constraint);

                // Submit the query to the autocomplete API and retrieve a PendingResult that will
                // contain the results when the query completes.
                PendingResult<AutocompletePredictionBuffer> results =
                        Places.GeoDataApi
                                .getAutocompletePredictions(mGoogleApiClient, constraint.toString(),
                                        mBounds, mPlaceFilter);

                // This method should have been called off the main UI thread. Block and wait for at most 60s
                // for a result from the API.
                AutocompletePredictionBuffer autocompletePredictions = results
                        .await(60, TimeUnit.SECONDS);

                // Confirm that the query completed successfully, otherwise return null
                final Status status = autocompletePredictions.getStatus();
                if (!status.isSuccess()) {
                    Toast.makeText(getContext(), "Error contacting API: " + status.toString(),
                            Toast.LENGTH_SHORT).show();
                    //Log.e(TAG, "Error getting autocomplete prediction API call: " + status.toString());
                    autocompletePredictions.release();
                    return null;
                }

                //Log.i(TAG, "Query completed. Received " + autocompletePredictions.getCount() + " predictions.");

                // Freeze the results immutable representation that can be stored safely.
                return DataBufferUtils.freezeAndClose(autocompletePredictions);
            }
            //Log.e(TAG, "Google API client is not connected for autocomplete query.");
            return null;
        }


    }

}
