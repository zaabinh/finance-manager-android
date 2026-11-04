package com.example.fintrack.ui.preview;

import android.view.View;
import android.widget.TextView;
import com.example.fintrack.R;

import java.util.List;

/** Layouts rendered by {@link LayoutRenderTest}. */
final class PreviewShots {

    private PreviewShots() { }

    static List<LayoutRenderTest.Shot> all() {
        return LayoutRenderTest.list(
                new LayoutRenderTest.Shot("feedback_inline", "view_feedback_inline", false, null).fonts(),
                new LayoutRenderTest.Shot("transaction_list", "activity_transaction_list", true,
                        root -> root.findViewById(R.id.navTabHistory).setSelected(true)),
                new LayoutRenderTest.Shot("transaction_detail", "activity_transaction_detail", true, null),
                new LayoutRenderTest.Shot("transaction_form", "activity_transaction_form", true, null),
                new LayoutRenderTest.Shot("transaction_transfer", "activity_transaction_form", true, root -> {
                    root.findViewById(R.id.groupExpenseIncome).setVisibility(View.GONE);
                    root.findViewById(R.id.groupTransfer).setVisibility(View.VISIBLE);
                    ((com.google.android.material.button.MaterialButton) root.findViewById(R.id.btnTypeExpense)).setChecked(false);
                    ((com.google.android.material.button.MaterialButton) root.findViewById(R.id.btnTypeTransfer)).setChecked(true);
                    root.findViewById(R.id.tvAmountSign).setVisibility(View.GONE);
                    ((TextView) root.findViewById(R.id.tvAmountLabel)).setText(R.string.transfer_amount_label);
                    ((TextView) root.findViewById(R.id.btnSaveTransaction)).setText(R.string.transfer_save);
                    ((com.google.android.material.appbar.MaterialToolbar) root.findViewById(R.id.toolbarTransactionForm)).setTitle(R.string.transfer_add_title);
                }),
                new LayoutRenderTest.Shot("receipt_import", "activity_receipt_import", true, null),
                new LayoutRenderTest.Shot("receipt_review", "activity_receipt_review", true, null),
                new LayoutRenderTest.Shot("budget_list", "activity_budget_list", true, null),
                new LayoutRenderTest.Shot("budget_form", "activity_budget_form", true, root -> {
                    ((TextView) root.findViewById(R.id.tvAmountLabel)).setText(R.string.budget_limit_label);
                    root.findViewById(R.id.tvAmountSign).setVisibility(View.GONE);
                }),
                new LayoutRenderTest.Shot("budget_detail", "activity_budget_detail", true, null),
                new LayoutRenderTest.Shot("transaction_item", "item_transaction", false, null),
                new LayoutRenderTest.Shot("budget_item", "item_budget", false, null)
        );
    }
}
