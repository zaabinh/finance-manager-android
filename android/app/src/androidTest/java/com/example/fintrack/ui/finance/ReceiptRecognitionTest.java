package com.example.fintrack.ui.finance;

import static org.junit.Assert.assertEquals;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.fintrack.app.transaction.domain.ReceiptParser;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ReceiptRecognitionTest {
    @Test public void bundledModelReadsAnImageBeforeSuggestingAmount() throws Exception {
        Bitmap image = Bitmap.createBitmap(1000, 350, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image); canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(64);
        canvas.drawText("QUAN DONG", 40, 100, paint); canvas.drawText("TOTAL 95000", 40, 230, paint);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            Text recognized = Tasks.await(recognizer.process(InputImage.fromBitmap(image, 0)), 45, TimeUnit.SECONDS);
            assertEquals(95_000, ReceiptParser.parse(recognized.getText()).amount);
        } finally { recognizer.close(); image.recycle(); }
    }
}
