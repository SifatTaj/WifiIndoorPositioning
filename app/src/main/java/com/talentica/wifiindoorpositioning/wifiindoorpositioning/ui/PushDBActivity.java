package com.talentica.wifiindoorpositioning.wifiindoorpositioning.ui;

import android.print.PrintDocumentAdapter;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.R;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.model.AccessPoint;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.model.IndoorProject;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.model.ReferencePoint;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmList;

public class PushDBActivity extends AppCompatActivity {

    Button btnPush;
    TextView tvStatus;
    EditText etCollection;

    String projectId;
    Boolean dbReady = false;

    StitchAppClient client;
    RemoteMongoClient mongoClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_push_db);

        btnPush = findViewById(R.id.btn_push);
        etCollection = findViewById(R.id.et_collection);
        tvStatus = findViewById(R.id.tv_db_status);

        projectId = getIntent().getStringExtra("projectId");

        initDB();

        btnPush.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                push();
            }
        });
    }

    protected void initDB() {
        client = Stitch.initializeDefaultAppClient("rssi-lgpnr");
        mongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas-rssi");
    }

    protected void push() {
        String collection = etCollection.getText().toString();

        final Realm realm = Realm.getDefaultInstance();
        IndoorProject project = realm.where(IndoorProject.class).equalTo("id", projectId).findFirst();
        final RealmList<AccessPoint> accessPointsFromRealm = project.getAps();
        final RealmList<ReferencePoint> referencePointsFromRealm = project.getRps();

        final RemoteMongoCollection<Document> apCollection = mongoClient.getDatabase("bracu").getCollection(collection + "_ap");
        final RemoteMongoCollection<Document> rpCollection = mongoClient.getDatabase("bracu").getCollection(collection + "_rp");
        client.getAuth().loginWithCredential(new AnonymousCredential()).addOnSuccessListener(new OnSuccessListener<StitchUser>() {
            @Override
            public void onSuccess(StitchUser stitchUser) {
                Toast.makeText(getApplicationContext(), "Login", Toast.LENGTH_SHORT).show();
            }
        });

        for (AccessPoint accessPoint : accessPointsFromRealm) {
            Document doc = new Document();
            doc.put("ssid", accessPoint.getSsid());
            doc.put("mac",accessPoint.getMac_address());
            doc.put("description", accessPoint.getDescription());
            doc.put("x", accessPoint.getX());
            doc.put("y", accessPoint.getY());
            apCollection.insertOne(doc);
        }

        for (ReferencePoint referencePoint : referencePointsFromRealm) {

            List<Double> readings = new ArrayList<>();
            for (AccessPoint accessPoint : referencePoint.getReadings()) {
                DBObject dbObject = new BasicDBObject(accessPoint.getMac_address(), accessPoint.getMeanRss());
                readings.add(accessPoint.getMeanRss());
            }

            Document doc = new Document();
            doc.put("name", referencePoint.getName());
            doc.put("description", referencePoint.getDescription());
            doc.put("x", referencePoint.getX());
            doc.put("y", referencePoint.getY());
            doc.put("readings", readings);
            rpCollection.insertOne(doc);
        }
    }
}
