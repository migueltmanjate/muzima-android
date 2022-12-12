/*
 * Copyright (c) The Trustees of Indiana University, Moi University
 * and Vanderbilt University Medical Center. All Rights Reserved.
 *
 * This version of the code is licensed under the MPL 2.0 Open Source license
 * with additional health care disclaimer.
 * If the user is an entity intending to commercialize any application that uses
 * this code in a for-profit venture, please contact the copyright holder.
 */

package com.muzima.view.patients;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.muzima.MuzimaApplication;
import com.muzima.R;
import com.muzima.adapters.ListAdapter;
import com.muzima.adapters.forms.ClientSummaryFormsAdapter;
import com.muzima.adapters.relationships.RelationshipsAdapter;
import com.muzima.api.model.*;
import com.muzima.controller.*;
import com.muzima.model.AvailableForm;
import com.muzima.model.CompleteForm;
import com.muzima.model.collections.AvailableForms;
import com.muzima.model.collections.CompleteForms;
import com.muzima.model.location.MuzimaGPSLocation;
import com.muzima.service.MuzimaGPSLocationService;
import com.muzima.tasks.FormsLoaderService;
import com.muzima.utils.*;
import com.muzima.utils.smartcard.SmartCardIntentIntegrator;
import com.muzima.view.MainDashboardActivity;
import com.muzima.view.custom.ActivityWithPatientSummaryBottomNavigation;
import com.muzima.view.custom.MuzimaRecyclerView;
import com.muzima.view.forms.FormViewIntent;
import com.muzima.view.forms.FormsWithDataActivity;
import com.muzima.view.fragments.patient.ChronologicalObsViewFragment;
import com.muzima.view.relationship.RelationshipsListActivity;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.muzima.adapters.forms.FormsPagerAdapter.TAB_COMPLETE;
import static com.muzima.adapters.forms.FormsPagerAdapter.TAB_INCOMPLETE;
import static com.muzima.utils.ConceptUtils.getConceptNameFromConceptNamesByLocale;
import static com.muzima.utils.DateUtils.SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT;
import static com.muzima.utils.RelationshipViewUtil.listOnClickListener;
import static com.muzima.view.relationship.RelationshipsListActivity.INDEX_PATIENT;

public class PatientSummaryActivity extends ActivityWithPatientSummaryBottomNavigation implements ClientSummaryFormsAdapter.OnFormClickedListener, FormsLoaderService.FormsLoadedCallback, ListAdapter.BackgroundListQueryTaskListener {
    private static final String TAG = "PatientSummaryActivity";
    public static final String PATIENT = "patient";
    public static final String PATIENT_UUID = "patient_uuid";
    public static final String CALLING_ACTIVITY = "calling_activity_key";
    public static final boolean DEFAULT_SHR_STATUS = false;
    private static final boolean DEFAULT_RELATIONSHIP_STATUS = false;
    private TextView patientNameTextView;
    private ImageView patientGenderImageView;
    private TextView dobTextView;
    private TextView identifierTextView;
    private TextView gpsAddressTextView;
    private TextView ageTextView;
    private TextView incompleteFormsCountView;
    private TextView completeFormsCountView;
    private String patientUuid;
    private Patient patient;
    private final LanguageUtil languageUtil = new LanguageUtil();
    private View incompleteFormsView;
    private View completeFormsView;
    private ClientSummaryFormsAdapter formsAdapter;
    private List<AvailableForm> forms = new ArrayList<>();
    private ListView lvwPatientRelationships;
    private RelationshipsAdapter patientRelationshipsAdapter;
    private View noDataView;
    private Person selectedRelatedPerson;
    private TextView patientAddress;
    private TextView patientPhoneNumber;
    private TextView testingSector;
    private TextView preferredTestingLocation;
    private TextView testingDate;
    private TextView lastConsentDate;

    private TextView confidantName;

    private TextView confidantContact1;

    private TextView lastCVResult;

    private TextView lastCVResultDate;

    private TextView lastARVPickup;

    private TextView lastARVPickupDispenseMode;

    private TextView nextARVPickupDate;

    private TextView lastClinicalConsultDate;

    private TextView nextClinicalConsultDate;

    private TextView tptStartDate;

    private TextView tptEndDate;

    private String applicationLanguage;
    private ConceptController conceptController;
    private ObservationController observationController;
    private EncounterController encounterController;
    private boolean isFGHCustomClientSummaryEnabled;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.getInstance().onCreate(this, true);
        languageUtil.onCreate(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_summary);
        initializeResources();
        loadPatientData();
        initializeView();
        loadData();
        loadChronologicalObsView();
        loadRelationships();
        setupStillLoadingView();
        setTitle(R.string.title_activity_client_summary);
        loadBottomNavigation(patientUuid);

        LinearLayout tagsLayout = findViewById(R.id.menu_tags);
        TagsUtil.loadTags(patient, tagsLayout, getApplicationContext());
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            EventBus.getDefault().register(this);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFormsCount();
        patientRelationshipsAdapter.reloadData();
    }

    private void loadData() {
        ((MuzimaApplication) this.getApplicationContext()).getExecutorService()
                .execute(new FormsLoaderService(this.getApplicationContext(), this));
    }

    private void loadChronologicalObsView() {
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.replace(R.id.chronological_fragment, ChronologicalObsViewFragment.newInstance(patientUuid, true)).commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_client_summary, menu);
        MenuItem shrMenu = menu.findItem(R.id.menu_shr);
        MenuItem relationshipMenu = menu.findItem(R.id.menu_relationship);
        MenuItem locationMenu = menu.findItem(R.id.menu_location_item);

        MuzimaSettingController muzimaSettingController = ((MuzimaApplication) getApplicationContext()).getMuzimaSettingController();
        boolean isSHRSettingEnabled = muzimaSettingController.isSHREnabled();
        boolean isRelationshipEnabled = muzimaSettingController.isRelationshipEnabled();
        boolean isGeomappingEnabled = muzimaSettingController.isGeoMappingEnabled();

        if (!isSHRSettingEnabled)
            shrMenu.setVisible(false);

        if (!isRelationshipEnabled)
            relationshipMenu.setVisible(false);

        if (!isGeomappingEnabled)
            locationMenu.setVisible(false);

        locationMenu.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.general_launching_map_message), Toast.LENGTH_SHORT).show();
                navigateToClientsLocationMap();
                return true;
            }
        });

        shrMenu.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                readSmartCard();
                return true;
            }
        });

        relationshipMenu.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                navigateToRelationshipsView();
                return true;
            }
        });
        return true;
    }

    private void readSmartCard() {
        SmartCardIntentIntegrator SHRIntegrator = new SmartCardIntentIntegrator(PatientSummaryActivity.this);
        SHRIntegrator.initiateCardRead();
        Toast.makeText(getApplicationContext(), getResources().getString(R.string.general_opening_card_reader), Toast.LENGTH_LONG).show();
    }

    private void navigateToClientsLocationMap() {
        Intent intent = new Intent(this, PatientLocationMapActivity.class);
        intent.putExtra(PATIENT,patient);
        startActivity(intent);
    }

    private void navigateToRelationshipsView() {
        Intent intent = new Intent(this, RelationshipsListActivity.class);
        intent.putExtra(PATIENT, patient);
        startActivity(intent);
    }

    @Override
    public void onUserInteraction() {
        ((MuzimaApplication) getApplication()).restartTimer();
        super.onUserInteraction();
    }

    @SuppressLint("SuspiciousIndentation")
    private void loadPatientData() {
        try {
            patientUuid = getIntent().getStringExtra(PATIENT_UUID);

            patient = ((MuzimaApplication) getApplicationContext()).getPatientController().getPatientByUuid(patientUuid);
            patientNameTextView.setText(patient.getDisplayName());
            identifierTextView.setText(String.format(Locale.getDefault(), "ID:#%s", patient.getIdentifier()));

            if (patient.getBirthdate() != null)
                ageTextView.setText(getString(R.string.general_years, String.format(Locale.getDefault(), "%d ", DateUtils.calculateAge(patient.getBirthdate()))));
                dobTextView.setText(getString(R.string.general_date_of_birth, String.format(" %s", new SimpleDateFormat("MM-dd-yyyy", Locale.getDefault()).format(patient.getBirthdate()))));
            patientGenderImageView.setImageResource(getGenderImage(patient.getGender()));

            if (patient.getAddresses().size() > 0) {
                int index = patient.getAddresses().size() - 1;
                patientAddress.setText(getFormattedPatientAddress(patient.getAddresses().get(index)));
            }

            if (patient.getAttribute("e2e3fd64-1d5f-11e0-b929-000c29ad1d07") != null) {
                patientPhoneNumber.setText(patient.getAttribute("e2e3fd64-1d5f-11e0-b929-000c29ad1d07").getAttribute());
            }

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            applicationLanguage = preferences.getString(getResources().getString(R.string.preference_app_language), getResources().getString(R.string.language_english));

            conceptController = ((MuzimaApplication) getApplicationContext()).getConceptController();
            observationController = ((MuzimaApplication) getApplicationContext()).getObservationController();
            encounterController =  ((MuzimaApplication) getApplicationContext()).getEncounterController();

            testingSector.setText(getObsByPatientUuidAndConceptId(patientUuid, 23877));
            preferredTestingLocation.setText(getObsByPatientUuidAndConceptId(patientUuid, 21155));
            testingDate.setText(getObsByPatientUuidAndConceptId(patientUuid, 23879));
            lastConsentDate.setText(getObsByPatientUuidAndConceptId(patientUuid, 23775));

            // Confidant Information
            String cName = getObsByPatientUuidAndConceptId(patientUuid, 1740);
            confidantName.setText((StringUtils.EMPTY.equalsIgnoreCase(cName) || cName == null)?"-----------------":cName);
            String cContact = getObsByPatientUuidAndConceptId(patientUuid, 6224);
            confidantContact1.setText((StringUtils.EMPTY.equalsIgnoreCase(cContact) || cContact == null)?"-----------------":cContact);

            // Clinical Information

            // FSR
            Observation cvResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndEncounterTypeUuid(patientUuid, 1305, "b5b7d21f-efd1-407e-81ce-ba9d93c524f8");
            if(cvResultObs!=null) {
                String cvResult = getConceptNameFromConceptNamesByLocale(cvResultObs.getValueCoded().getConceptNames(), applicationLanguage);
                lastCVResult.setText((StringUtils.EMPTY.equalsIgnoreCase(cvResult) || cvResult == null)?"-----------------":cvResult);
            }
            else{
                lastCVResult.setText("-----------------");
            }

            if(cvResultObs==null) {
                cvResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndEncounterTypeUuid(patientUuid, 856, "b5b7d21f-efd1-407e-81ce-ba9d93c524f8");
                if(cvResultObs!=null) {
                    String value = cvResultObs.getValueNumeric().toString();
                    lastCVResult.setText((StringUtils.EMPTY.equalsIgnoreCase(value) || value == null)?"-----------------":value);
                }
                else{
                    lastCVResult.setText("-----------------");
                }
            }

            if(cvResultObs!=null){
            Date cvResultDate = cvResultObs.getObservationDatetime();
            if(cvResultDate!=null) {
                String value = DateUtils.getFormattedDate(cvResultDate, SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT);
                lastCVResultDate.setText((StringUtils.EMPTY.equalsIgnoreCase(value) || value == null)?"-----------------":value);
            }
            else{
                lastCVResultDate.setText("-----------------");
            }
            }
            else{
                lastCVResultDate.setText("-----------------");
            }

            // Ficha Lab
            cvResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndEncounterTypeUuid(patientUuid, 1305, "e2790f68-1d5f-11e0-b929-000c29ad1d07");
            if(cvResultObs!=null) {
                String cvResult = getConceptNameFromConceptNamesByLocale(cvResultObs.getValueCoded().getConceptNames(), applicationLanguage);
                lastCVResult.setText((StringUtils.EMPTY.equalsIgnoreCase(cvResult) || cvResult == null)?"-----------------":cvResult);
            }
            if(cvResultObs==null) {
                cvResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndEncounterTypeUuid(patientUuid, 856, "e2790f68-1d5f-11e0-b929-000c29ad1d07");
                if(cvResultObs!=null) {
                    String value = cvResultObs.getValueNumeric().toString();
                    lastCVResult.setText((StringUtils.EMPTY.equalsIgnoreCase(value) || value == null)?"-----------------":value);
                }
                else{
                    lastCVResult.setText("-----------------");
                }
            }

            if(cvResultObs!=null){
            Date cvResultDate = cvResultObs.getObservationDatetime();
            if(cvResultDate!=null) {
                String value = DateUtils.getFormattedDate(cvResultDate, SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT);
                lastCVResultDate.setText((StringUtils.EMPTY.equalsIgnoreCase(value) || value == null)?"-----------------":value);
            }
            else{
                lastCVResultDate.setText("-----------------");
            }
            }
            else{
                lastCVResultDate.setText("-----------------");
            }

            //FICHA CLINICA
            Observation consultationResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndEncounterTypeUuid(patientUuid, 1305, "e278f956-1d5f-11e0-b929-000c29ad1d07");
            if(consultationResultObs==null) {
                consultationResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndEncounterTypeUuid(patientUuid, 856, "e278f956-1d5f-11e0-b929-000c29ad1d07");
            }
            if(consultationResultObs!=null) {
                Date lastClinicalConsult = consultationResultObs.getObservationDatetime();
                if (lastClinicalConsult != null) {
                    String value = DateUtils.getFormattedDate(lastClinicalConsult, SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT);
                    lastClinicalConsultDate.setText((StringUtils.EMPTY.equalsIgnoreCase(value) || value == null)?"-----------------":value);
                }
                else{
                    lastClinicalConsultDate.setText("-----------------");
                }
            }
            else{
                lastClinicalConsultDate.setText("-----------------");
            }

            consultationResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndEncounterTypeUuid(patientUuid, 1410, "e278f956-1d5f-11e0-b929-000c29ad1d07");
            if(consultationResultObs!=null){
            Date nextClinicalConsult = consultationResultObs.getObservationDatetime();
            if(nextClinicalConsult!=null) {
                String value = DateUtils.getFormattedDate(nextClinicalConsult, SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT);
                nextClinicalConsultDate.setText((StringUtils.EMPTY.equalsIgnoreCase(value) || value == null)?"-----------------":value);
            }
            else{
                nextClinicalConsultDate.setText("-----------------");
            }
            }
            else{
                nextClinicalConsultDate.setText("-----------------");
            }


            //FILA
            Observation lastARVPickupObs = getEncounterDateTimeByPatientUuidAndConceptIdAndEncounterTypeUuid(patientUuid, 165174, "e279133c-1d5f-11e0-b929-000c29ad1d07");
            if(lastARVPickupObs!=null){
            Date lastARVPickupDate = lastARVPickupObs.getObservationDatetime();
            if(lastARVPickupDate!=null) {
                String value = DateUtils.getFormattedDate(lastARVPickupDate, SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT);
                lastARVPickup.setText((StringUtils.EMPTY.equalsIgnoreCase(value) || value == null)?"-----------------":value);
            }
            else{
                lastARVPickup.setText("-----------------");
            }
            }
            else{
                lastARVPickup.setText("-----------------");
            }

            if(lastARVPickupObs!=null) {
                String dispenseModeValue = getConceptNameFromConceptNamesByLocale(lastARVPickupObs.getValueCoded().getConceptNames(), applicationLanguage);
                lastARVPickupDispenseMode.setText((StringUtils.EMPTY.equalsIgnoreCase(dispenseModeValue) || dispenseModeValue == null)?"-----------------":dispenseModeValue);
                String value = getObsByPatientUuidAndConceptId(patientUuid, 5096);
                nextARVPickupDate.setText((StringUtils.EMPTY.equalsIgnoreCase(value) || value == null)?"-----------------":value);
            }
            else{
                lastARVPickupDispenseMode.setText("-----------------");
                nextARVPickupDate.setText("-----------------");
            }

            // FICHA RESUMO
            Observation tptStartDateResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndValuedCoded(patientUuid, 165308, "1256");
            tptStartDate.setText(tptStartDateResultObs!=null?DateUtils.getFormattedDate(tptStartDateResultObs.getObservationDatetime(), SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT):"------------------------");
            Observation tptEndDateResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndValuedCoded(patientUuid, 165308, "1267");
            tptEndDate.setText(tptEndDateResultObs!=null?DateUtils.getFormattedDate(tptEndDateResultObs.getObservationDatetime(), SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT):"------------------------");

            // FICHA CLINICA
            tptStartDateResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndValuedCoded(patientUuid, 165308, "INICIAR");
            tptStartDate.setText(tptStartDateResultObs!=null?DateUtils.getFormattedDate(tptStartDateResultObs.getObservationDatetime(), SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT):"------------------------");

            tptEndDateResultObs = getEncounterDateTimeByPatientUuidAndConceptIdAndValuedCoded(patientUuid, 165308, "COMPLETO");
            tptEndDate.setText(tptEndDateResultObs!=null?DateUtils.getFormattedDate(tptEndDateResultObs.getObservationDatetime(), SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT):"------------------------");

        } catch (PatientController.PatientLoadException e) {
            Log.e(getClass().getSimpleName(), "Exception encountered while loading patients ", e);
        } catch (ObservationController.LoadObservationException e) {
            Log.e(getClass().getSimpleName(), "Exception encountered while loading patients ", e);
        } catch (JSONException e) {
            Log.e(getClass().getSimpleName(), "JSONException encountered ", e);
        }
    }

    private String getFormattedPatientAddress(PersonAddress personAddress) {
        String formattedAddress = "";
        if (!StringUtils.isEmpty(personAddress.getAddress6())) {
            formattedAddress = formattedAddress + " " + personAddress.getAddress6() + ";";
        }

        if (!StringUtils.isEmpty(personAddress.getAddress5())) {
            formattedAddress = formattedAddress + " " + personAddress.getAddress5() + ";";
        }

        if (!StringUtils.isEmpty(personAddress.getAddress3())) {
            formattedAddress = formattedAddress + " " + personAddress.getAddress3() + ";";
        }
        if (!StringUtils.isEmpty(personAddress.getAddress1())) {
            formattedAddress = formattedAddress + " " + personAddress.getAddress1() + ";";
        }

        return formattedAddress;
    }

    private String getObsByPatientUuidAndConceptId(String patientUuid, int conceptId) throws JSONException, ObservationController.LoadObservationException {
        List<Observation> observations = new ArrayList<>();
        try {
            observations = observationController.getObservationsByPatientuuidAndConceptId(patientUuid, conceptId);
            Concept concept = conceptController.getConceptById(conceptId);
            Collections.sort(observations, observationDateTimeComparator);
            if (observations.size() > 0) {
                Observation obs = observations.get(0);
                if (concept.isDatetime())
                    return DateUtils.getFormattedDate(obs.getValueDatetime(), SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT);
                else if (concept.isCoded())
                    return getConceptNameFromConceptNamesByLocale(obs.getValueCoded().getConceptNames(), applicationLanguage);
                else if (concept.isNumeric())
                    return String.valueOf(obs.getValueNumeric());
                else
                    return obs.getValueText();
            }
        } catch (ObservationController.LoadObservationException | Exception |
                 ConceptController.ConceptFetchException e) {
            Log.e(getClass().getSimpleName(), "Exception occurred while loading observations", e);
        }
        return StringUtils.EMPTY;
    }

    private final Comparator<Observation> observationDateTimeComparator = new Comparator<Observation>() {
        @Override
        public int compare(Observation lhs, Observation rhs) {
            return -lhs.getObservationDatetime().compareTo(rhs.getObservationDatetime());
        }
    };

    private final Comparator<Encounter> encounterDateTimeComparator = new Comparator<Encounter>() {
        @Override
        public int compare(Encounter lhs, Encounter rhs) {
            return -lhs.getEncounterDatetime().compareTo(rhs.getEncounterDatetime());
        }
    };

    private int getGenderImage(String gender) {
        return gender.equalsIgnoreCase("M") ? R.drawable.gender_male : R.drawable.gender_female;
    }

    private String getDistanceToClientAddress(Patient patient) {
        try {
            MuzimaGPSLocation currentLocation = getCurrentGPSLocation();
            PersonAddress personAddress = patient.getPreferredAddress();
            if (currentLocation != null && personAddress != null && !StringUtils.isEmpty(personAddress.getLatitude()) && !StringUtils.isEmpty(personAddress.getLongitude())) {
                double startLatitude = Double.parseDouble(currentLocation.getLatitude());
                double startLongitude = Double.parseDouble(currentLocation.getLongitude());
                double endLatitude = Double.parseDouble(personAddress.getLatitude());
                double endLongitude = Double.parseDouble(personAddress.getLongitude());

                float[] results = new float[1];
                Location.distanceBetween(startLatitude, startLongitude, endLatitude, endLongitude, results);
                return String.format("%.02f", results[0] / 1000) + " km";
            }
        } catch (NumberFormatException e) {
            Log.e(getClass().getSimpleName(), "Number format exception ", e);
        }
        return "";
    }

    private Observation getEncounterDateTimeByPatientUuidAndConceptIdAndEncounterTypeUuid(String patientUuid, int conceptId, String encounterTypeUuid) {
        try {
              List<Observation> observations = observationController.getObservationsByPatientuuidAndConceptId(patientUuid, conceptId);
              Collections.sort(observations, observationDateTimeComparator);
            if (observations.size() > 0) {
                for (Observation observation:observations) {
                    EncounterType encounterType = observation.getEncounter().getEncounterType();
                    if(encounterTypeUuid.equalsIgnoreCase(encounterType.getUuid())){
                        return observation;
                    }
                }
            }
        }
        catch (ObservationController.LoadObservationException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private Observation getEncounterDateTimeByPatientUuidAndConceptIdAndValuedCoded(String patientUuid, int conceptId, String valueCoded) {
        try {
            List<Observation> observations = observationController.getObservationsByPatientuuidAndConceptId(patientUuid, conceptId);
            Collections.sort(observations, observationDateTimeComparator);
            if (observations.size() > 0) {
                for (Observation observation:observations) {
                    System.out.println(observation.getValueCoded().getName());
                    if(observation.getValueCoded().getName().equalsIgnoreCase(valueCoded)){
                        return observation;
                    }
                }
            }
        }
        catch (ObservationController.LoadObservationException e) {
            throw new RuntimeException(e);
        }

        return null;
    }


    private MuzimaGPSLocation getCurrentGPSLocation() {
        MuzimaGPSLocationService muzimaLocationService = ((MuzimaApplication) getApplicationContext())
                .getMuzimaGPSLocationService();
        return muzimaLocationService.getLastKnownGPSLocation();
    }

    private void initializeResources() {
        isFGHCustomClientSummaryEnabled = ((MuzimaApplication) getApplication().getApplicationContext()).getMuzimaSettingController().isFGHCustomClientSummaryEnabled();
        boolean isFGHCustomClientAddressEnabled = ((MuzimaApplication) getApplication().getApplicationContext()).getMuzimaSettingController().isFGHCustomClientAddressEnabled();
        patientNameTextView = findViewById(R.id.name);
        patientGenderImageView = findViewById(R.id.genderImg);
        dobTextView = findViewById(R.id.dateOfBirth);
        identifierTextView = findViewById(R.id.identifier);
        ageTextView = findViewById(R.id.age_text_label);
        gpsAddressTextView = findViewById(R.id.distanceToClientAddress);
        incompleteFormsCountView = findViewById(R.id.dashboard_forms_incomplete_forms_count_view);
        completeFormsCountView = findViewById(R.id.dashboard_forms_complete_forms_count_view);
        incompleteFormsView = findViewById(R.id.dashboard_forms_incomplete_forms_view);
        completeFormsView = findViewById(R.id.dashboard_forms_complete_forms_view);
        patientAddress = findViewById(R.id.patient_address);
        patientPhoneNumber = findViewById(R.id.patient_phone_number);
        testingSector = findViewById(R.id.sector_value);
        preferredTestingLocation = findViewById(R.id.preferred_testing_location);
        testingDate = findViewById(R.id.testing_date_value);
        lastConsentDate = findViewById(R.id.last_consent_date_value);
        confidantName = findViewById(R.id.confidant_name_value);
        confidantContact1 = findViewById(R.id.confidant_phone_number_1_value);
        lastCVResult = findViewById(R.id.most_recent_viral_load_result_value);
        lastCVResultDate = findViewById(R.id.most_recent_viral_load_result_date_value);
        lastARVPickup = findViewById(R.id.most_recent_arv_pickup_value);
        lastARVPickupDispenseMode = findViewById(R.id.most_recent_arv_pickup_dispensation_mode_value);
        nextARVPickupDate = findViewById(R.id.next_scheduled_arv_pickup_value);
        lastClinicalConsultDate = findViewById(R.id.most_recent_clinical_consultation_date_value);
        nextClinicalConsultDate = findViewById(R.id.next_clinical_consultation_date_value);
        tptStartDate = findViewById(R.id.tpt_start_date_value);
        tptEndDate = findViewById(R.id.tpt_end_date_value);
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        LinearLayout dadosDeConsentimento = findViewById(R.id.dados_de_consentimento);
        RelativeLayout addressLayout = findViewById(R.id.address_layout);
        RelativeLayout phoneNumberLayout = findViewById(R.id.phone_number_layout);

        if (!isFGHCustomClientSummaryEnabled) {
            dadosDeConsentimento.setVisibility(View.GONE);
        }

        if (!isFGHCustomClientAddressEnabled) {
            addressLayout.setVisibility(View.GONE);
            phoneNumberLayout.setVisibility(View.GONE);
        }

        incompleteFormsView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchFormDataList(true);
            }
        });

        completeFormsView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchFormDataList(false);
            }
        });

        MuzimaRecyclerView formsListRecyclerView = findViewById(R.id.recycler_list);
        formsListRecyclerView.setLayoutManager(new LinearLayoutManager(this.getApplicationContext(), LinearLayoutManager.VERTICAL, false));

        formsAdapter = new ClientSummaryFormsAdapter(forms, this);
        formsListRecyclerView.setAdapter(formsAdapter);
        formsListRecyclerView.setNoDataLayout(findViewById(R.id.no_data_layout),
                getString(R.string.info_forms_unavailable),
                getString(R.string.info_no_forms_data_tip));

        ImageView noDataImage = findViewById(R.id.no_data_image);
        noDataImage.setVisibility(View.GONE);

        TextView textView = findViewById(R.id.no_data_msg);
        Typeface typeface = ResourcesCompat.getFont(this, R.font.roboto_light);
        textView.setTypeface(typeface);
    }

    public void initializeView() {
        LinearLayout historicalData = findViewById(R.id.historical_data);
        LinearLayout dataCollection = findViewById(R.id.data_collection);
        LinearLayout relationshipList = findViewById(R.id.relationships_listing);
        LinearLayout confidantLayout = findViewById(R.id.confidant_details_layout);
        LinearLayout clinicalInfoLayout = findViewById(R.id.clinical_information_layout);
        View historicalDataSeparator = findViewById(R.id.historical_data_separator);
        View relationshipListingSeparator = findViewById(R.id.relationships_list_separator);
        View confidantDetailsSeparator = findViewById(R.id.confidant_details_separator);
        View confidantNameSeparator = findViewById(R.id.confidant_name_textview_separator);
        View confidantContact1Separator = findViewById(R.id.confidant_phone_number_1_textview_separator);
        View clinicalInfoSeparator = findViewById(R.id.clinical_information_separator);
        View lastCVResultVSeparator = findViewById(R.id.most_recent_viral_load_result_separator);
        View lastCVResultSeparator = findViewById(R.id.most_recent_viral_load_result_date_separator);
        View lastCVResultDateSeparator = findViewById(R.id.most_recent_clinical_consultation_date_separator);
        View lastARVPickupSeparator = findViewById(R.id.most_recent_arv_pickup_separator);
        View lastARVPickupDispenseModeSeparator = findViewById(R.id.most_recent_arv_pickup_dispensation_mode_separator);
        View nextARVPickupDateSeparator = findViewById(R.id.next_scheduled_arv_pickup_separator);
        View lastClinicalConsultDateSeparator = findViewById(R.id.most_recent_clinical_consultation_date_separator);
        View nextClinicalConsultDateSeparator = findViewById(R.id.next_clinical_consultation_date_separator);
        View tptStartDateSeparator = findViewById(R.id.tpt_start_date_separator);
        View tptEndDateSeparator = findViewById(R.id.tpt_end_date_separator);

        boolean isContactListingOnPatientSummary = ((MuzimaApplication) getApplication().getApplicationContext()).getMuzimaSettingController().isContactListingOnPatientSummaryEnabled();
        boolean isObsListingOnPatientSummary = ((MuzimaApplication) getApplication().getApplicationContext()).getMuzimaSettingController().isObsListingOnPatientSummaryEnabled();
        boolean isConfidantInformationListingOnPatientSummary = ((MuzimaApplication) getApplication().getApplicationContext()).getMuzimaSettingController().isFGHCustomConfidantOptionEnabled();
        boolean isClinicalInformationListingOnPatientSummary = ((MuzimaApplication) getApplication().getApplicationContext()).getMuzimaSettingController().isFGHCustomClinicalOptionEnabled();

        if (isFGHCustomClientSummaryEnabled) {
            LinearLayout.LayoutParams relationshipParam = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    100
            );
            relationshipList.setLayoutParams(relationshipParam);
        } else {
            if (isContactListingOnPatientSummary && isObsListingOnPatientSummary) {
                historicalDataSeparator.setVisibility(View.VISIBLE);
                relationshipListingSeparator.setVisibility(View.VISIBLE);
                LinearLayout.LayoutParams relationshipParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        40
                );
                relationshipList.setLayoutParams(relationshipParam);

                LinearLayout.LayoutParams obsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        40
                );
                historicalData.setLayoutParams(obsParam);

                LinearLayout.LayoutParams formsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        30
                );
                dataCollection.setLayoutParams(formsParam);
            } else if (isContactListingOnPatientSummary && !isObsListingOnPatientSummary) {
                historicalDataSeparator.setVisibility(View.GONE);
                relationshipListingSeparator.setVisibility(View.VISIBLE);
                LinearLayout.LayoutParams relationshipParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        60
                );
                relationshipList.setLayoutParams(relationshipParam);

                LinearLayout.LayoutParams obsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        0
                );
                historicalData.setLayoutParams(obsParam);

                LinearLayout.LayoutParams formsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        40
                );
                dataCollection.setLayoutParams(formsParam);
            } else if (!isContactListingOnPatientSummary && isObsListingOnPatientSummary) {
                historicalDataSeparator.setVisibility(View.VISIBLE);
                relationshipListingSeparator.setVisibility(View.GONE);
                LinearLayout.LayoutParams relationshipParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        0
                );
                relationshipList.setLayoutParams(relationshipParam);

                LinearLayout.LayoutParams obsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        60
                );
                historicalData.setLayoutParams(obsParam);

                LinearLayout.LayoutParams formsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        40
                );
                dataCollection.setLayoutParams(formsParam);
            }
            else if (isConfidantInformationListingOnPatientSummary && !isClinicalInformationListingOnPatientSummary) {
                confidantDetailsSeparator.setVisibility(View.VISIBLE);
                confidantNameSeparator.setVisibility(View.VISIBLE);
                confidantContact1Separator.setVisibility(View.VISIBLE);
                clinicalInfoSeparator.setVisibility(View.VISIBLE);
                lastCVResultVSeparator.setVisibility(View.VISIBLE);
                lastCVResultSeparator.setVisibility(View.VISIBLE);
                lastCVResultDateSeparator.setVisibility(View.VISIBLE);
                lastARVPickupSeparator.setVisibility(View.VISIBLE);
                lastARVPickupDispenseModeSeparator.setVisibility(View.VISIBLE);
                nextARVPickupDateSeparator.setVisibility(View.VISIBLE);
                lastClinicalConsultDateSeparator.setVisibility(View.VISIBLE);
                nextClinicalConsultDateSeparator.setVisibility(View.VISIBLE);
                tptStartDateSeparator.setVisibility(View.VISIBLE);
                tptEndDateSeparator.setVisibility(View.VISIBLE);
                LinearLayout.LayoutParams confidantParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        100
                );
                confidantLayout.setLayoutParams(confidantParam);

                LinearLayout.LayoutParams clinicalInfoParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        0
                );
                clinicalInfoLayout.setLayoutParams(clinicalInfoParam);

                LinearLayout.LayoutParams formsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        0
                );
                dataCollection.setLayoutParams(formsParam);
            }
            else if (!isConfidantInformationListingOnPatientSummary && isClinicalInformationListingOnPatientSummary) {
                confidantDetailsSeparator.setVisibility(View.VISIBLE);
                confidantNameSeparator.setVisibility(View.VISIBLE);
                confidantContact1Separator.setVisibility(View.VISIBLE);
                clinicalInfoSeparator.setVisibility(View.VISIBLE);
                lastCVResultVSeparator.setVisibility(View.VISIBLE);
                lastCVResultSeparator.setVisibility(View.VISIBLE);
                lastCVResultDateSeparator.setVisibility(View.VISIBLE);
                lastARVPickupSeparator.setVisibility(View.VISIBLE);
                lastARVPickupDispenseModeSeparator.setVisibility(View.VISIBLE);
                nextARVPickupDateSeparator.setVisibility(View.VISIBLE);
                lastClinicalConsultDateSeparator.setVisibility(View.VISIBLE);
                nextClinicalConsultDateSeparator.setVisibility(View.VISIBLE);
                tptStartDateSeparator.setVisibility(View.VISIBLE);
                tptEndDateSeparator.setVisibility(View.VISIBLE);
                LinearLayout.LayoutParams confidantParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        0
                );
                confidantLayout.setLayoutParams(confidantParam);

                LinearLayout.LayoutParams clinicalInfoParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        100
                );
                clinicalInfoLayout.setLayoutParams(clinicalInfoParam);

                LinearLayout.LayoutParams formsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        0
                );
                dataCollection.setLayoutParams(formsParam);
            }
            else if (isConfidantInformationListingOnPatientSummary && isClinicalInformationListingOnPatientSummary) {
                confidantDetailsSeparator.setVisibility(View.VISIBLE);
                confidantNameSeparator.setVisibility(View.VISIBLE);
                confidantContact1Separator.setVisibility(View.VISIBLE);
                clinicalInfoSeparator.setVisibility(View.VISIBLE);
                lastCVResultVSeparator.setVisibility(View.VISIBLE);
                lastCVResultSeparator.setVisibility(View.VISIBLE);
                lastCVResultDateSeparator.setVisibility(View.VISIBLE);
                lastARVPickupSeparator.setVisibility(View.VISIBLE);
                lastARVPickupDispenseModeSeparator.setVisibility(View.VISIBLE);
                nextARVPickupDateSeparator.setVisibility(View.VISIBLE);
                lastClinicalConsultDateSeparator.setVisibility(View.VISIBLE);
                nextClinicalConsultDateSeparator.setVisibility(View.VISIBLE);
                tptStartDateSeparator.setVisibility(View.VISIBLE);
                tptEndDateSeparator.setVisibility(View.VISIBLE);
                LinearLayout.LayoutParams confidantParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        35
                );
                confidantLayout.setLayoutParams(confidantParam);

                LinearLayout.LayoutParams clinicalInfoParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        65
                );
                clinicalInfoLayout.setLayoutParams(clinicalInfoParam);

                LinearLayout.LayoutParams formsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        0
                );
                dataCollection.setLayoutParams(formsParam);
            } else {
                historicalDataSeparator.setVisibility(View.GONE);
                relationshipListingSeparator.setVisibility(View.GONE);
                LinearLayout.LayoutParams relationshipParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        0
                );
                relationshipList.setLayoutParams(relationshipParam);

                LinearLayout.LayoutParams obsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        0
                );
                historicalData.setLayoutParams(obsParam);

                LinearLayout.LayoutParams formsParam = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        100
                );
                dataCollection.setLayoutParams(formsParam);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (android.R.id.home == item.getItemId()) {
            if (getIntent().getStringExtra(CALLING_ACTIVITY) != null && getIntent().getStringExtra(CALLING_ACTIVITY).equalsIgnoreCase(PatientsSearchActivity.class.getSimpleName())) {
                Intent intent = new Intent(getApplicationContext(), PatientsSearchActivity.class);
                startActivity(intent);
                finish();
            } else if (getIntent().getStringExtra(CALLING_ACTIVITY) != null && getIntent().getStringExtra(CALLING_ACTIVITY).equalsIgnoreCase(MainDashboardActivity.class.getSimpleName())) {
                Intent intent = new Intent(getApplicationContext(), MainDashboardActivity.class);
                startActivity(intent);
                finish();
            } else if (getIntent().getStringExtra(CALLING_ACTIVITY) != null && getIntent().getStringExtra(CALLING_ACTIVITY).equalsIgnoreCase(RelationshipsListActivity.class.getSimpleName())) {
                Intent intent = new Intent(getApplicationContext(), MainDashboardActivity.class);
                startActivity(intent);
                finish();
            }
            onBackPressed();
        }
        return true;
    }

    private void loadFormsCount() {
        try {
            long incompleteForms = ((MuzimaApplication) getApplicationContext()).getFormController().countIncompleteFormsForPatient(patientUuid);
            long completeForms = ((MuzimaApplication) getApplicationContext()).getFormController().countCompleteFormsForPatient(patientUuid);
            incompleteFormsCountView.setText(String.valueOf(incompleteForms));

            if (incompleteForms == 0) {
                incompleteFormsView.setBackground(getResources().getDrawable(R.drawable.rounded_corners_green));
            } else if (incompleteForms > 0 && incompleteForms <= 5) {
                incompleteFormsView.setBackground(getResources().getDrawable(R.drawable.rounded_corners_orange));
            } else {
                incompleteFormsView.setBackground(getResources().getDrawable(R.drawable.rounded_corners_red));
            }

            incompleteFormsCountView.setText(String.valueOf(incompleteForms));

            if (completeForms == 0) {
                completeFormsView.setBackground(getResources().getDrawable(R.drawable.rounded_corners_green));
            } else if (completeForms > 0 && completeForms <= 5) {
                completeFormsView.setBackground(getResources().getDrawable(R.drawable.rounded_corners_orange));
            } else {
                completeFormsView.setBackground(getResources().getDrawable(R.drawable.rounded_corners_red));
            }
            completeFormsCountView.setText(String.valueOf(completeForms));

            if ((incompleteForms == 0 && completeForms == 0) || isFGHCustomClientSummaryEnabled) {
                completeFormsView.setVisibility(View.GONE);
                incompleteFormsView.setVisibility(View.GONE);
            } else {
                completeFormsView.setVisibility(View.VISIBLE);
                incompleteFormsView.setVisibility(View.VISIBLE);
            }
        } catch (FormController.FormFetchException e) {
            Log.e(getClass().getSimpleName(), "Could not count complete and incomplete forms", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FormsWithDataActivity.FORM_VIEW_ACTIVITY_RESULT) {
            loadFormsCount();
        }
    }


    private void launchFormDataList(boolean isIncompleteFormsData) {
        Intent intent = new Intent(this, FormsWithDataActivity.class);

        intent.putExtra(PATIENT_UUID, patientUuid);
        intent.putExtra(PATIENT, patient);
        if (isIncompleteFormsData) {
            intent.putExtra(FormsWithDataActivity.KEY_FORMS_TAB_TO_OPEN, TAB_INCOMPLETE);
        } else {
            intent.putExtra(FormsWithDataActivity.KEY_FORMS_TAB_TO_OPEN, TAB_COMPLETE);
        }
        startActivity(intent);
    }

    @Override
    public void onFormsLoaded(final AvailableForms formList) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                forms.addAll(formList);
                formsAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onFormClickedListener(int position) {
        AvailableForm form = forms.get(position);
        Intent intent = new FormViewIntent(this, form, patient, false);
        intent.putExtra(INDEX_PATIENT, patient);
        this.startActivityForResult(intent, FormsWithDataActivity.FORM_VIEW_ACTIVITY_RESULT);
    }

    @Override
    protected int getBottomNavigationMenuItemId() {
        return R.id.action_cohorts;
    }

    public void loadForms(View v) {
        Intent intent = new Intent(this, DataCollectionActivity.class);
        intent.putExtra(PATIENT_UUID, patientUuid);
        startActivity(intent);
    }

    public void loadObservation(View v) {
        Intent intent = new Intent(this, ObsViewActivity.class);
        intent.putExtra(PATIENT_UUID, patientUuid);
        startActivity(intent);
    }

    public void navigateToRelationships(View v) {
        Intent intent = new Intent(this, RelationshipsListActivity.class);
        intent.putExtra(PATIENT, patient);
        startActivity(intent);
    }

    private void loadRelationships() {
        lvwPatientRelationships = findViewById(R.id.relationships_list);
        patientRelationshipsAdapter = new RelationshipsAdapter(this, R.layout.item_patients_list_multi_checkable, ((MuzimaApplication) getApplicationContext()).getRelationshipController(),
                patient.getUuid(), ((MuzimaApplication) getApplicationContext()).getPatientController());
        patientRelationshipsAdapter.setBackgroundListQueryTaskListener(this);

        lvwPatientRelationships.setAdapter(patientRelationshipsAdapter);
        lvwPatientRelationships.setClickable(true);
        lvwPatientRelationships.setLongClickable(true);
        lvwPatientRelationships.setEmptyView(noDataView);
        lvwPatientRelationships.setOnItemClickListener(listOnClickListener(this, ((MuzimaApplication) getApplicationContext()), patient, false, lvwPatientRelationships));
    }

    private void setupNoDataView() {
        TextView noDataMsgTextView = findViewById(R.id.no_relationship_data_msg);
        Typeface typeface = ResourcesCompat.getFont(this, R.font.roboto_light);
        noDataMsgTextView.setText(getResources().getText(R.string.info_relationships_unavailable));
        noDataMsgTextView.setVisibility(View.VISIBLE);
        noDataMsgTextView.setTypeface(typeface);
    }

    private void setupStillLoadingView() {
        TextView noDataMsgTextView = findViewById(R.id.no_relationship_data_msg);
        noDataMsgTextView.setText(R.string.general_loading_relationships);
    }

    @Override
    public void onQueryTaskStarted() {

    }

    @Override
    public void onQueryTaskFinish() {
        if (patientRelationshipsAdapter.isEmpty())
            setupNoDataView();
    }

    @Override
    public void onQueryTaskCancelled() {

    }

    @Override
    public void onQueryTaskCancelled(Object errorDefinition) {

    }
}
