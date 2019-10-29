package com.talentica.wifiindoorpositioning.wifiindoorpositioning.ui;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.R;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.model.AccessPoint;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.model.IndoorProject;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.model.ReferencePoint;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;

public class PushDBActivity extends AppCompatActivity {

    Button btnPush;
    TextView tvStatus;
    EditText etCollection, etDB;
    ProgressBar progressBar;

    String projectId;
    Boolean dbReady = false;
    double uploadProgress = 0;
    Document doc;

    StitchAppClient client;
    RemoteMongoClient mongoClient;

    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_push_db);

        btnPush = findViewById(R.id.btn_push);
        etCollection = findViewById(R.id.et_collection);
        etDB = findViewById(R.id.et_db);
        tvStatus = findViewById(R.id.tv_upload_status);
        progressBar = findViewById(R.id.pb_upload);

        projectId = getIntent().getStringExtra("projectId");

        IndoorProject project = Realm.getDefaultInstance().where(IndoorProject.class).equalTo("id", projectId).findFirst();
        etDB.setText(project.getName());
        etCollection.setText(project.getName());

        initApi();

        btnPush.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                push();
            }
        });
    }

    protected void initApi() {
        client = Stitch.initializeDefaultAppClient("rssi-lgpnr");
        mongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas-rssi");
    }

    protected void push() {
        String collection = etCollection.getText().toString();

        final Realm realm = Realm.getDefaultInstance();
        IndoorProject project = realm.where(IndoorProject.class).equalTo("id", projectId).findFirst();
        final RealmList<AccessPoint> accessPointsFromRealm = project.getAps();
        final RealmList<ReferencePoint> referencePointsFromRealm = project.getRps();

        final String dbName = etDB.getText().toString();

        final RemoteMongoCollection<Document> apCollection = mongoClient.getDatabase(dbName).getCollection(collection + "_ap");
        final RemoteMongoCollection<Document> rpCollection = mongoClient.getDatabase(dbName).getCollection(collection + "_rp");
        client.getAuth().loginWithCredential(new AnonymousCredential()).addOnSuccessListener(new OnSuccessListener<StitchUser>() {
            @Override
            public void onSuccess(StitchUser stitchUser) {
                Toast.makeText(getApplicationContext(), "Authorised Access", Toast.LENGTH_SHORT).show();
            }
        });

        double size = accessPointsFromRealm.size() + referencePointsFromRealm.size();
        double incr = 100.0 / size;

        for (AccessPoint accessPoint : accessPointsFromRealm) {
            doc = new Document();
            doc.put("ssid", accessPoint.getSsid());
            doc.put("mac",accessPoint.getMac_address());
            doc.put("description", accessPoint.getDescription());
            doc.put("x", accessPoint.getX());
            doc.put("y", accessPoint.getY());
            apCollection.insertOne(doc).addOnSuccessListener(new OnSuccessListener<RemoteInsertOneResult>() {
                @Override
                public void onSuccess(RemoteInsertOneResult remoteInsertOneResult) {
                    progressBar.setProgress((int) uploadProgress);
                    tvStatus.setText("Uploaded");
                }
            });
            uploadProgress += incr;
        }

        for (ReferencePoint referencePoint : referencePointsFromRealm) {

            List<Double> readings = new ArrayList<>();
            for (AccessPoint accessPoint : referencePoint.getReadings()) {
                readings.add(accessPoint.getMeanRss());
            }

            Document doc = new Document();
            doc.put("name", referencePoint.getName());
            doc.put("description", referencePoint.getDescription());
            doc.put("x", referencePoint.getX());
            doc.put("y", referencePoint.getY());
            doc.put("readings", readings);
            rpCollection.insertOne(doc).addOnSuccessListener(new OnSuccessListener<RemoteInsertOneResult>() {
                @Override
                public void onSuccess(RemoteInsertOneResult remoteInsertOneResult) {
                    progressBar.setProgress((int) uploadProgress);
                }
            });
            uploadProgress += incr;
        }
    }
}
