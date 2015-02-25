package com.svz.green.veravoice;

import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.svz.green.veravoice.model.Model;
import com.svz.green.veravoice.model.ModelState;
import com.svz.green.veravoice.recognizer.Command;
import com.svz.green.veravoice.recognizer.Commands;
import com.svz.green.veravoice.recognizer.DataFiles;
import com.svz.green.veravoice.recognizer.Grammar;
import com.svz.green.veravoice.recognizer.PhonMapper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

public class MainActivity extends ActionBarActivity implements RecognitionListener {

    private static final String COMMAND_SEARCH = "command";
    private static final String KWS_SEARCH = "hotword";

    private MainActivity instance;
    private TextView commandTextView;

    private SpeechRecognizer mRecognizer;
    private TextToSpeech mTextToSpeech;

    private final Handler mHandler = new Handler();
    private final Queue<String> mSpeechQueue = new LinkedList<>();

    private Commands commands;

    ///
    /// Листенеры
    ///

    private final Runnable mStopRecognitionCallback = new Runnable() {
        @Override
        public void run() {
            stopRecognition();
        }
    };

    private final TextToSpeech.OnUtteranceCompletedListener mUtteranceCompletedListener = new TextToSpeech.OnUtteranceCompletedListener() {
        @Override
        public void onUtteranceCompleted(String utteranceId) {
            synchronized (mSpeechQueue) {
                mSpeechQueue.poll();
                if (mSpeechQueue.isEmpty()) {
//                    mRecognizer.startListening(KWS_SEARCH);
//                    mRecognizer.startListening(COMMAND_SEARCH);
                    startRecognition();
                }
            }
        }
    };

    ///
    /// Активити
    ///

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        instance = this;

        mTextToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.ERROR) {
                    return;
                } else {
                    Toast.makeText(instance, "Статус загрузки = " + status, Toast.LENGTH_SHORT).show();
                }
                Locale locale = new Locale("ru");
                //if (mTextToSpeech.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE) {}
                int result = mTextToSpeech.setLanguage(locale);
                if (result == mTextToSpeech.LANG_MISSING_DATA || result == mTextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(instance, "Языковой пакет не загружен", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    Toast.makeText(instance, "Статус загрузки локали = " + result, Toast.LENGTH_SHORT).show();
                }
                mTextToSpeech.setOnUtteranceCompletedListener(mUtteranceCompletedListener);
            }
        });

        commandTextView = (TextView) findViewById(R.id.command);

        setupRecognizer();
    }

    @Override
    protected void onDestroy() {
        if (mRecognizer != null) {
            mRecognizer.cancel();
        }
        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
            mTextToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    ///
    /// Настройка
    ///

    private void setupRecognizer() {
        final String hotword = getString(R.string.hotword);
        commands = new Commands(this);

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {

                    if(!isSDCARDAvailable()) {
                        setCurrentCommand("Недоступен");
                    } else {
                        setCurrentCommand("Доступен");
                    }

                    List<Command> commandsList = commands.getCommands();
                    final String[] names = new String[commandsList.size()];
                    for (int i = 0; i < names.length; i++) {
                        names[i] = commandsList.get(i).getText();
                    }

                    PhonMapper phonMapper = new PhonMapper(getAssets().open("dict/ru/hotwords"));
                    Grammar grammar = new Grammar(names, phonMapper);
                    grammar.addWords(hotword);
                    DataFiles dataFiles = new DataFiles(getPackageName(), "ru");
                    File hmmDir = new File(dataFiles.getHmm());
                    File dict = new File(dataFiles.getDict());
                    File jsgf = new File(dataFiles.getJsgf());
                    copyAssets(hmmDir);
                    saveFile(jsgf, grammar.getJsgf());
                    saveFile(dict, grammar.getDict());
                    mRecognizer = SpeechRecognizerSetup.defaultSetup()
                            .setAcousticModel(hmmDir)
                            .setSampleRate(8000)
                            .setDictionary(dict)
                            .setBoolean("-remove_noise", false)
                            .setKeywordThreshold(1e-7f)
                            .getRecognizer();
                    mRecognizer.addKeyphraseSearch(KWS_SEARCH, hotword);
                    mRecognizer.addGrammarSearch(COMMAND_SEARCH, jsgf);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception ex) {
                if (ex != null) {
                    onRecognizerSetupError(ex);
                } else {
                    onRecognizerSetupComplete();
                }
            }
        }.execute();
    }

    private void onRecognizerSetupComplete() {
        Toast.makeText(this, "Ready", Toast.LENGTH_SHORT).show();
        setCurrentCommand(getString(R.string.ready));

        mRecognizer.addListener(this);
//        mRecognizer.startListening(KWS_SEARCH);
//        mRecognizer.startListening(COMMAND_SEARCH);
        startRecognition();
    }

    private void onRecognizerSetupError(Exception ex) {
        Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void copyAssets(File baseDir) throws IOException {
        String[] files = getAssets().list("hmm/ru");

        for (String fromFile : files) {
            File toFile = new File(baseDir.getAbsolutePath() + "/" + fromFile);
            InputStream in = getAssets().open("hmm/ru/" + fromFile);
            FileUtils.copyInputStreamToFile(in, toFile);
        }
    }

    private void saveFile(File f, String content) throws IOException {
        File dir = f.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory: " + dir);
        }
        FileUtils.writeStringToFile(f, content, "UTF8");
    }

    public static boolean isSDCARDAvailable(){
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)? true :false;
    }

    ///
    /// Распознавание
    ///

    @Override
    public void onBeginningOfSpeech() {
        setCurrentCommand("Разговор");
    }

    @Override
    public void onEndOfSpeech() {
        setCurrentCommand("Тишина");

        if (mRecognizer.getSearchName().equals(COMMAND_SEARCH)) {
            mRecognizer.stop();
        }
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null) return;

//        String text = hypothesis.getHypstr();
//        if (KWS_SEARCH.equals(mRecognizer.getSearchName())) {
//            startRecognition();
//        } else {
//            setCurrentCommand(text);
//        }
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        //mHandler.removeCallbacks(mStopRecognitionCallback);

//        if (hypothesis != null) {
//            int score = hypothesis.getBestScore();
//            Toast.makeText(this, "Score: " + score, Toast.LENGTH_SHORT).show();
//        }
        mRecognizer.stop();
        mRecognizer.cancel();

        String text = hypothesis != null ? hypothesis.getHypstr() : null;

        if (COMMAND_SEARCH.equals(mRecognizer.getSearchName())) {
            if (text != null) {
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

                // TODO: states
                String answer = null;

                if (text.equals(getString(R.string.command_activate_1)) || text.equals(getString(R.string.command_activate_2))) {
                    if (Model.setState(ModelState.ACTIVATE)) {
                        answer = getString(R.string.answer_activate);
                    }
                } else if (text.equals(getString(R.string.command_start_1)) || text.equals(getString(R.string.command_start_2))) {
                    if (Model.setState(ModelState.START)) {
                        answer = getString(R.string.answer_start) + " " + getString(R.string.answer_article) + " " + Model.getData().getCurrent().getName();
                    }
                } else if (text.equals(getString(R.string.command_confirm_1)) || text.equals(getString(R.string.command_confirm_2)) || text.equals(getString(R.string.command_confirm_3))) {
                    if (Model.getState() == ModelState.START) {
                        if (Model.getData().getNext() != null) {
                            answer = getString(R.string.answer_article) + " " + Model.getData().getCurrent().getName();
                        } else {
                            Model.setState(ModelState.STOP);
                            answer = getString(R.string.answer_stop);
                        }
                    }
                } else if (text.equals(getString(R.string.command_repeat_1)) || text.equals(getString(R.string.command_repeat_2)) || text.equals(getString(R.string.command_repeat_3))) {
                    if (Model.getState() == ModelState.START) {
                        answer = getString(R.string.answer_article) + " " + Model.getData().getCurrent().getName();
                    }
                } else if (text.equals(getString(R.string.command_stop_1)) || text.equals(getString(R.string.command_stop_2))) {
                    if (Model.setState(ModelState.STOP)) {
                        answer = getString(R.string.answer_stop);
                    }
                } else {
                    //answer = getString(R.string.answer_undefined);
                }

                if (answer != null) {
                    process(answer);
                }
            }


//            mRecognizer.startListening(COMMAND_SEARCH);
            startRecognition();
        }
    }


    private synchronized void startRecognition() {
////        if (mRecognizer == null || COMMAND_SEARCH.equals(mRecognizer.getSearchName())) return;
//        if (mRecognizer == null) return;
//        mRecognizer.cancel();
//        //new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME).startTone(ToneGenerator.TONE_CDMA_PIP, 200);
//        setCurrentCommand(getString(R.string.ready_for_command));


        if (mRecognizer == null) return;
        mRecognizer.cancel();
//        post(400, new Runnable() {
//            @Override
//            public void run(){
                mRecognizer.startListening(COMMAND_SEARCH);
                post(1000, mStopRecognitionCallback);
//            }
//        });
    }

    private synchronized void stopRecognition() {
//        if (mRecognizer == null || KWS_SEARCH.equals(mRecognizer.getSearchName())) return;
        if (mRecognizer == null) return;
        mRecognizer.stop();
    }

    private void post(long delay, Runnable task) {
        mHandler.postDelayed(task, delay);
    }

    ///
    /// Синтез речи
    ///

    private void speak(String text) {
        synchronized (mSpeechQueue) {
            mRecognizer.stop();
            mSpeechQueue.add(text);
            HashMap<String, String> params = new HashMap<>(2);
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UUID.randomUUID().toString());
            params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
            params.put(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS, "true");
            mTextToSpeech.speak(text, TextToSpeech.QUEUE_ADD, params);
        }
    }

    ///
    /// Утилиты
    ///

    private void setCurrentCommand(String command) {
        if (commandTextView != null) {
            if (command != null && !command.isEmpty()) {
                commandTextView.setText("" + command);
            } else {
                commandTextView.setText(R.string.command_undefined);
            }
        }
    }

    private void process(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        speak(text);
    }
}
