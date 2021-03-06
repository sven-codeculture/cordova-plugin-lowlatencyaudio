/*
THIS SOFTWARE IS PROVIDED BY ANDREW TRICE "AS IS" AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL ANDREW TRICE OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.rjfun.cordova.plugin;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import android.content.Intent;
import com.google.android.vending.expansion.downloader.FakeR;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;

import com.android.vending.expansion.zipfile.APKExpansionSupport;
import com.android.vending.expansion.zipfile.ZipResourceFile;
import com.google.android.vending.expansion.downloader.Helpers;

/**
 * @author triceam
 *
 */
public class LowLatencyAudio extends CordovaPlugin {

    public static final String ERROR_NO_AUDIOID="A reference does not exist for the specified audio id.";
    public static final String ERROR_AUDIOID_EXISTS="A reference already exists for the specified audio id.";

    public static final String PRELOAD_FX="preloadFX";
    public static final String PRELOAD_AUDIO="preloadAudio";
    public static final String PLAY="play";
    public static final String STOP="stop";
    public static final String LOOP="loop";
    public static final String UNLOAD="unload";
    public static final String ASSETCHECK="assetCheck";
    public static String androidKey = "";

    public static final int DEFAULT_POLYPHONY_VOICES = 15;

    private static final String LOGTAG = "LowLatencyAudio";
    public static final int REQUEST_CODE = 234256412;

    private static SoundPool soundPool;
    private static HashMap<String, LowLatencyAudioAsset> assetMap;
    private static HashMap<String, Integer> soundMap;
    private static HashMap<String, ArrayList<Integer>> streamMap;

    final static int mainVersion = 1;
    final static int patchVersion = 1;

    private PluginResult executeAssetCheck(JSONArray data) {
        try {
            LowLatencyDownloaderService.BASE64_PUBLIC_KEY = data.getString(0);

            Intent intent = new Intent("com.rjfun.cordova.plugin.LowLatencyAudio.VIEW");
            intent.addCategory(Intent.CATEGORY_DEFAULT);

            Log.d(LOGTAG, "Starting intend " + intent);
            Log.d(LOGTAG, "Key is: " + LowLatencyDownloaderService.BASE64_PUBLIC_KEY);

            this.cordova.startActivityForResult((CordovaPlugin) this, intent, REQUEST_CODE);
            return new PluginResult(Status.OK);
        } catch (Exception e) {
            return new PluginResult(Status.ERROR, e.getMessage());
        }
    }

    private PluginResult executePreloadFX(JSONArray data) {
        String audioID;
        try {
            audioID = data.getString(0);
            if (!soundMap.containsKey(audioID)) {
                String assetPath = data.getString(1);
                String fullPath = "www/".concat(assetPath);

                Log.d(LOGTAG, "preloadFX - " + audioID + ": " + assetPath);

                Context ctx = cordova.getActivity().getApplicationContext();
                AssetManager am = ctx.getResources().getAssets();
                AssetFileDescriptor afd = am.openFd(fullPath);
                int assetIntID = soundPool.load(afd, 1);
                soundMap.put(audioID, assetIntID);
            } else {
                return new PluginResult(Status.ERROR, ERROR_AUDIOID_EXISTS);
            }
        } catch (JSONException e) {
            return new PluginResult(Status.ERROR, e.toString());
        } catch (IOException e) {
            return new PluginResult(Status.ERROR, e.toString());
        }

        return new PluginResult(Status.OK);
    }

    private PluginResult executePreloadAudio(JSONArray data) {
        String audioID;
        try {
            audioID = data.getString(0);
            if (!assetMap.containsKey(audioID)) {
                String assetPath = data.getString(1);
                Log.d(LOGTAG, "preloadAudio - " + audioID + ": " + assetPath);

                int voices;
                if (data.length() < 2) {
                    voices = 0;
                } else {
                    voices = data.getInt(2);
                }

                String fullPath;
                AssetFileDescriptor afd;
                Context ctx = cordova.getActivity().getApplicationContext();
                if(assetPath.startsWith("~/")) {
                    afd = this.getExternalAssets(ctx, assetPath.substring(2));
                    if(afd == null) {
                        throw new FileNotFoundException(assetPath);
                    }
                } else {
                    fullPath = "www/".concat(assetPath);
                    AssetManager am = ctx.getResources().getAssets();
                    afd = am.openFd(fullPath);
                }
                LowLatencyAudioAsset asset = new LowLatencyAudioAsset(
                        afd, voices);
                assetMap.put(audioID, asset);

                return new PluginResult(Status.OK);
            } else {
                return new PluginResult(Status.ERROR, ERROR_AUDIOID_EXISTS);
            }
        } catch (JSONException e) {
            return new PluginResult(Status.ERROR, e.toString());
        } catch (IOException e) {
            return new PluginResult(Status.ERROR, e.toString());
        } catch (Exception e) {
            return new PluginResult(Status.ERROR, e.toString());
        }
    }

    private PluginResult executePlayOrLoop(String action, JSONArray data) {
        String audioID;
        try {
            audioID = data.getString(0);
            //Log.d( LOGTAG, "play - " + audioID );

            if (assetMap.containsKey(audioID)) {
                LowLatencyAudioAsset asset = assetMap.get(audioID);
                if (LOOP.equals(action))
                    asset.loop();
                else
                    asset.play();
            } else if (soundMap.containsKey(audioID)) {
                int loops = 0;
                if (LOOP.equals(action)) {
                    loops = -1;
                }

                ArrayList<Integer> streams = streamMap.get(audioID);
                if (streams == null)
                    streams = new ArrayList<Integer>();

                int assetIntID = soundMap.get(audioID);
                int streamID = soundPool
                        .play(assetIntID, 1, 1, 1, loops, 1);
                streams.add(streamID);
                streamMap.put(audioID, streams);
            } else {
                return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
            }
        } catch (JSONException e) {
            return new PluginResult(Status.ERROR, e.toString());
        } catch (IOException e) {
            return new PluginResult(Status.ERROR, e.toString());
        }

        return new PluginResult(Status.OK);
    }

    private PluginResult executeStop(JSONArray data) {
        String audioID;
        try {
            audioID = data.getString(0);
            //Log.d( LOGTAG, "stop - " + audioID );

            if (assetMap.containsKey(audioID)) {
                LowLatencyAudioAsset asset = assetMap.get(audioID);
                asset.stop();
            } else if (soundMap.containsKey(audioID)) {
                ArrayList<Integer> streams = streamMap.get(audioID);
                if (streams != null) {
                    for (int x = 0; x < streams.size(); x++)
                        soundPool.stop(streams.get(x));
                }
                streamMap.remove(audioID);
            } else {
                return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
            }
        } catch (JSONException e) {
            return new PluginResult(Status.ERROR, e.toString());
        } catch (IOException e) {
            return new PluginResult(Status.ERROR, e.toString());
        }

        return new PluginResult(Status.OK);
    }

    private PluginResult executeUnload(JSONArray data) {
        String audioID;
        try {
            audioID = data.getString(0);
            Log.d( LOGTAG, "unload - " + audioID );

            if (assetMap.containsKey(audioID)) {
                LowLatencyAudioAsset asset = assetMap.get(audioID);
                asset.unload();
                assetMap.remove(audioID);
            } else if (soundMap.containsKey(audioID)) {
                // streams unloaded and stopped above
                int assetIntID = soundMap.get(audioID);
                soundMap.remove(audioID);
                soundPool.unload(assetIntID);
            } else {
                return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
            }
        } catch (JSONException e) {
            return new PluginResult(Status.ERROR, e.toString());
        } catch (IOException e) {
            return new PluginResult(Status.ERROR, e.toString());
        }

        return new PluginResult(Status.OK);
    }

    @Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "Plugin Called: " + action);

        PluginResult result = null;
        initSoundPool();

        //Init FakeR Helper is its still not Initialized
        Context ctx = cordova.getActivity().getApplicationContext();
        if(Helpers.fakeR == null) {
            Helpers.fakeR = new FakeR(ctx);
        }

        try {
            if (PRELOAD_FX.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        callbackContext.sendPluginResult( executePreloadFX(data) );
                    }
                });

            } else if (PRELOAD_AUDIO.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        callbackContext.sendPluginResult( executePreloadAudio(data) );
                    }
                });

            } else if (PLAY.equals(action) || LOOP.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        callbackContext.sendPluginResult( executePlayOrLoop(action, data) );
                    }
                });

            } else if (STOP.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        callbackContext.sendPluginResult( executeStop(data) );
                    }
                });

            } else if (UNLOAD.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        executeStop(data);
                        callbackContext.sendPluginResult( executeUnload(data) );
                    }
                });
            } else if (ASSETCHECK.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        Log.d(LOGTAG, "Starting AssetCheck");
                        callbackContext.sendPluginResult( executeAssetCheck(data) );
                    }
                });

            } else {
                Log.d(LOGTAG, "Action "+action+" not found");
                result = new PluginResult(Status.OK);
            }
        } catch (Exception ex) {
            result = new PluginResult(Status.ERROR, ex.toString());
        }

        if(result != null) callbackContext.sendPluginResult( result );
        return true;
    }

    private void initSoundPool() {
        if (soundPool == null) {
            soundPool = new SoundPool(DEFAULT_POLYPHONY_VOICES,
                    AudioManager.STREAM_MUSIC, 1);
        }

        if (soundMap == null) {
            soundMap = new HashMap<String, Integer>();
        }

        if (streamMap == null) {
            streamMap = new HashMap<String, ArrayList<Integer>>();
        }

        if (assetMap == null) {
            assetMap = new HashMap<String, LowLatencyAudioAsset>();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE) {
            Log.d(LOGTAG, String.valueOf(resultCode));
        }
    }

    private AssetFileDescriptor getExternalAssets(Context ctx, String filename) throws IOException {
        // Get APKExpensionFile
        ZipResourceFile expansionFile = APKExpansionSupport.getAPKExpansionZipFile(ctx, LowLatencyAudio.mainVersion, LowLatencyAudio.patchVersion);

        if (null == expansionFile) {
            Log.e(LOGTAG, "APKExpansionFile not found.");
            return null;
        }

        // Find file in ExpansionFile
        String fileName = Helpers.getExpansionAPKFileName(ctx, true, LowLatencyAudio.patchVersion);
        fileName = fileName.substring(0, fileName.lastIndexOf("."));
        AssetFileDescriptor file = expansionFile.getAssetFileDescriptor(fileName + "/" + filename);
        Log.d(LOGTAG, "External file: " + fileName + "/" + filename);

        return file;
    }
}
