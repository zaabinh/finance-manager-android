package com.example.fintrack.app.transaction.ui;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.transaction.domain.ReceiptParser;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public final class ReceiptViewModel extends AndroidViewModel {
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final MutableLiveData<Resource<ReceiptParser.Draft>> draft = new MutableLiveData<>();
    public ReceiptViewModel(@NonNull Application application) { super(application); }
    LiveData<Resource<ReceiptParser.Draft>> draft() { return draft; }
    void read(Uri image) {
        if (draft.getValue() != null) return;
        draft.setValue(Resource.loading(null));
        FinanceRepository.IO.execute(() -> {
            try {
                InputImage input = InputImage.fromFilePath(getApplication(), image);
                recognizer.process(input).addOnSuccessListener(text -> {
                    if (text.getText().trim().isEmpty()) draft.setValue(Resource.error("unreadable", null));
                    else draft.setValue(Resource.success(ReceiptParser.parse(text.getText())));
                }).addOnFailureListener(error -> draft.setValue(Resource.error("unreadable", null)));
            } catch (Exception error) { draft.postValue(Resource.error("unreadable", null)); }
        });
    }
    @Override protected void onCleared() { recognizer.close(); }
}
