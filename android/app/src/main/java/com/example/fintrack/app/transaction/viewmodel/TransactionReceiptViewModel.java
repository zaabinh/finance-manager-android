package com.example.fintrack.app.transaction.viewmodel;

import com.example.fintrack.app.core.error.AppError;

import android.content.Context;
import android.net.Uri;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.di.AppExecutors;
import com.example.fintrack.app.transaction.domain.TransactionParser;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * ViewModel quản lý quá trình đọc và trích xuất dữ liệu từ hình ảnh hóa đơn bằng Google ML Kit OCR.
 */
public final class TransactionReceiptViewModel extends ViewModel {
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final MutableLiveData<Resource<TransactionParser.Draft>> draft = new MutableLiveData<>();
    private final Context appContext;
    private final AppExecutors executors;

    public TransactionReceiptViewModel(Context appContext, AppExecutors executors) {
        this.appContext = appContext;
        this.executors = executors;
    }

    public LiveData<Resource<TransactionParser.Draft>> draft() { 
        return draft; 
    }

    public void read(Uri image) {
        if (draft.getValue() != null) return;
        draft.setValue(Resource.loading(null));
        executors.computation().execute(() -> {
            try {
                InputImage input = InputImage.fromFilePath(appContext, image);
                recognizer.process(input).addOnSuccessListener(text -> {
                    if (text.getText().trim().isEmpty()) draft.setValue(Resource.error(AppError.local(AppError.Category.DOMAIN, "OCR_UNREADABLE", null), null));
                    else draft.setValue(Resource.success(TransactionParser.parse(text.getText())));
                }).addOnFailureListener(error -> draft.setValue(Resource.error(AppError.local(AppError.Category.DOMAIN, "OCR_UNREADABLE", null), null)));
            } catch (Exception error) { draft.postValue(Resource.error(AppError.local(AppError.Category.DOMAIN, "OCR_UNREADABLE", null), null)); }
        });
    }

    @Override 
    protected void onCleared() { 
        recognizer.close(); 
    }
}
