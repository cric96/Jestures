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
package recorder.view;

import javax.swing.plaf.TreeUI;

import jestures.core.codification.GestureLength;
import jestures.core.view.ViewObserver;

/**
 *
 * The {@link RecordingViewObserver} class.
 *
 */
public interface RecordingViewObserver extends ViewObserver {

    /**
     * Set Recording.
     *
     * @param isRecording
     *            {@link TreeUI} if is recording
     */
    void setRecording(boolean isRecording);

    /**
     * Set the gesture length.
     * 
     * @param gestureLength
     *            the gesture length
     */
    void setGuiGestureLenght(GestureLength gestureLength);
}
