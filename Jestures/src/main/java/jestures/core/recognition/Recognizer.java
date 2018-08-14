/*******************************************************************************
 * Copyright (c) 2018 Giulianini Luca
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package jestures.core.recognition;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import com.google.common.base.Functions;
import com.google.gson.JsonSyntaxException;

import javafx.util.Pair;
import jestures.core.recognition.gesturedata.RecognitionSettingsImpl;
import jestures.core.serialization.Serializer;
import jestures.core.serialization.UserManager;
import jestures.core.tracking.Tracker;
import jestures.core.tracking.TrackerImpl;
import jestures.core.view.RecognitionViewObserver;
import jestures.core.view.screens.RecognitionScreenView;
import smile.math.distance.DynamicTimeWarping;

/**
 * A Recognizer is a simple tracker that can also perform a recognition task.
 *
 */
public final class Recognizer extends TrackerImpl implements Recognition {
    // private static final Logger LOG = Logger.getLogger(Recognizer.class);
    private final Serializer userManager;
    private static Recognition instance;
    private final Set<RecognitionViewObserver> view;
    private final Set<GestureListener> gestureListener;
    private Map<String, List<Vector2D[]>> userDataset;
    private long lastGestureTime;

    // RECOGNITION
    private final DynamicTimeWarping<Vector2D> dtw;
    private RecognitionSettingsImpl recognitionSettings;
    private boolean gestureRecognized;

    private Recognizer() {
        this.view = new HashSet<>();
        this.gestureListener = new HashSet<>();
        this.userManager = new UserManager();
        this.userDataset = null;
        this.lastGestureTime = 0;
        // RECOGNITION
        this.recognitionSettings = new RecognitionSettingsImpl(UpdateRate.FPS_10, 0, 0, 0, 0, 0);
        this.dtw = new DynamicTimeWarping<Vector2D>((a, b) -> a.distance(b), this.recognitionSettings.getDtwRadius());
        RecognitionScreenView.startFxThread();
    }

    /**
     * Get the instance.
     *
     * @return the {@link Tracker} instance.
     */
    public static Recognition getInstance() {
        synchronized (Tracker.class) {
            if (Recognizer.instance == null) {
                Recognizer.instance = new Recognizer();
            }
        }
        return Recognizer.instance;
    }

    @Override
    public void attacheUI(final RecognitionViewObserver view) {
        this.view.add(view);
        this.view.forEach(t -> t.refreshUsers());
    }

    // ############################################## FROM SENSOR ###################################
    @Override
    public void notifyOnSkeletonChange(final Vector2D primaryJoint, final Vector2D secondaryJoint) {
        super.notifyOnSkeletonChange(primaryJoint, secondaryJoint);
    }

    @Override
    public void notifyOnAccelerometerChange(final Vector3D acceleration) { // NOPMD
        super.notifyOnAccelerometerChange(acceleration);
    }

    // ############################################## FROM CODIFIER ###################################

    @Override
    public void notifyOnFrameChange(final int frame, final Queue<Vector2D> featureVector, final Vector2D derivative,
            final Vector2D distanceVector) {
        super.notifyOnFrameChange(frame, featureVector, derivative, distanceVector);
        this.view.forEach(t -> t.notifyOnFrameChange(frame, derivative, distanceVector));
        // QUI SI INNESTA IL RICONOSCIMENTO
        if ((frame + 1) % this.recognitionSettings.getUpdateRate().getFrameNumber() == 0) {
            final Vector2D[] arrayFeatureVector = new Vector2D[featureVector.size()];
            featureVector.toArray(arrayFeatureVector);
            final long currentSec = System.currentTimeMillis();
            if (this.gestureRecognized
                    && currentSec - this.lastGestureTime > this.recognitionSettings.getMinTimeSeparation()) {
                this.lastGestureTime = currentSec;
                this.recognize(arrayFeatureVector);
            } else if (!this.gestureRecognized) {
                this.lastGestureTime = currentSec;
                this.recognize(arrayFeatureVector);
            }
        }
    }

    @Override
    public void notifyOnFeatureVectorEvent(final List<Vector2D> featureVector) {
        super.notifyOnFeatureVectorEvent(featureVector);
        this.view.forEach(t -> t.notifyOnFeatureVectorEvent());
    }

    // ############################################# TRACKER #########################################
    @Override
    public boolean loadUserProfile(final String name) throws FileNotFoundException, IOException, JsonSyntaxException {
        final boolean userExists = this.userManager.loadOrCreateNewUser(name);
        this.userDataset = this.userManager.getDatasetForRecognition();
        this.recognitionSettings = this.userManager.getRecognitionSettings();
        this.view.forEach(t -> t.updateSettings(this.recognitionSettings));
        return userExists;
    }

    @Override
    public List<List<Vector2D>> getGestureDataset(final String gestureName) {
        return this.userManager.getGestureDataset(gestureName);
    }

    @Override
    public List<String> getAllUserGesture() {
        return this.userManager.getAllUserGestures();
    }

    @Override
    public String getUserName() {
        return this.userManager.getUserName();
    }

    // ############################################# TRACKER #########################################

    private void recognize(final Vector2D... featureVector) {
        final List<Pair<Double, String>> distances = new ArrayList<>();
        for (final String gestureName : this.userDataset.keySet()) {
            for (final Vector2D[] gestureTemplate : this.userDataset.get(gestureName)) {
                final double dtwDist = this.dtw.d(gestureTemplate, featureVector);
                if (dtwDist < this.recognitionSettings.getMaxDTWThreashold()
                        && dtwDist > this.recognitionSettings.getMinDtwThreashold()) {
                    distances.add(new Pair<Double, String>(dtwDist, gestureName));
                }
            }
        }

        if (distances.isEmpty()) {
            // Recognizer.LOG.debug("NO GESURES");
            this.gestureRecognized = false;
        } else {
            this.gestureRecognized = true;
        }
        distances.stream()
                 .map(t -> t.getValue())
                 .collect(Collectors.groupingBy(Functions.identity(), Collectors.counting()))
                 .entrySet()
                 .stream()
                 .filter(k -> k.getValue() > this.recognitionSettings.getMatchNumber()) // MATCH NUMBER
                 .max(Comparator.comparing(Entry::getValue))
                 .ifPresent(t -> {
                     this.gestureListener.forEach(k -> k.onGestureRecognized(t.getKey()));
                     this.view.forEach(k -> k.onGestureRecognized(t.getKey()));
                 });
    }

    @Override
    public void saveSettings() throws IOException {
        this.userManager.setRecognitionSettings(this.recognitionSettings);
    }

    @Override
    public void setDtwRadius(final double radius) {
        this.recognitionSettings.setDtwRadius(radius);

    }

    @Override
    public void setMinDtwThreashold(final int minDtwThreashold) {
        this.recognitionSettings.setMinDtwThreashold(minDtwThreashold);
    }

    @Override
    public void setMaxDtwThreashold(final int maxDtwThreashold) {
        this.recognitionSettings.setMaxDtwThreashold(maxDtwThreashold);
    }

    @Override
    public void setUpdateRate(final UpdateRate updateRate) {
        if (this.getFrameLength().getFrameNumber() % updateRate.getFrameNumber() == 0) {
            this.recognitionSettings.setUpdateRate(updateRate);
        } else {
            throw new IllegalStateException("Update rate must be a MCD of frame rate");
        }
    }

    @Override
    public void setMinTimeSeparation(final int minTimeSeparation) {
        this.recognitionSettings.setMinTimeSeparation(minTimeSeparation);
    }

    @Override
    public void setMatchNumber(final int matchNumber) {
        this.recognitionSettings.setMatchNumber(matchNumber);

    }

    @Override
    public void setOnGestureRecognized(final GestureListener listener) {
        this.gestureListener.add(listener);
    }

}