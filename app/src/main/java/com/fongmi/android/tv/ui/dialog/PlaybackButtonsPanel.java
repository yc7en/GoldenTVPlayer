package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogCommandButtonBinding;
import com.fongmi.android.tv.player.PlaybackCommands;
import com.fongmi.android.tv.player.exo.ExoCommand;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Task;
import com.google.android.material.button.MaterialButton;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class PlaybackButtonsPanel {

    private static final int LIST = 0, EDIT = 1, COMMAND = 2;
    private final DialogFragment owner;
    private DialogCommandButtonBinding binding;
    private SpaceItemDecoration itemDecoration;
    private ItemAdapter adapter;
    private Operations operations;
    private List<PlaybackCommands.Item> items = List.of();
    private PlaybackCommands.Item selected;
    private int page;
    private int itemSpacing = -1;
    private int listPosition;
    private int editPosition;
    private final List<Integer> actions = new ArrayList<>();
    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            WindowInsetsCompat insets = binding == null ? null : ViewCompat.getRootWindowInsets(binding.getRoot());
            if (editingPage() && insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
                Util.hideKeyboard(binding.title);
                return;
            }
            back();
        }
    };

    PlaybackButtonsPanel(DialogFragment owner) {
        this.owner = owner;
    }

    void bind(DialogCommandButtonBinding binding, Bundle savedInstanceState) {
        this.binding = binding;
        operations = new ViewModelProvider(owner).get(Operations.class);
        initItems();
        restoreState(savedInstanceState);
        operations.result.observe(owner.getViewLifecycleOwner(), this::onOperationResult);
        bindEvents();
        render(!isRoot());
        if (Util.isLeanback() && isRoot()) binding.primary.requestFocus();
    }

    private void initItems() {
        adapter = new ItemAdapter();
        binding.items.setAdapter(adapter);
        binding.items.setItemAnimator(null);
        itemDecoration = null;
        itemSpacing = -1;
        binding.title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(PlaybackCommands.MAX_TITLE_LENGTH)});
        binding.command.setFilters(new InputFilter[]{new InputFilter.LengthFilter(PlaybackCommands.MAX_COMMAND_LENGTH)});
    }

    private void bindEvents() {
        binding.primary.setOnClickListener(this::onPrimary);
        binding.title.setOnEditorActionListener(this::onEditorAction);
        binding.command.setOnEditorActionListener(this::onEditorAction);
        if (owner.requireDialog() instanceof ComponentDialog dialog) dialog.getOnBackPressedDispatcher().addCallback(owner.getViewLifecycleOwner(), backCallback);
    }

    private void restoreState(Bundle savedInstanceState) {
        Bundle state = savedInstanceState == null ? owner.requireArguments() : savedInstanceState;
        page = state.getInt("page", LIST);
        listPosition = state.getInt("listPosition");
        editPosition = state.getInt("editPosition");
        String id = state.getString("selected");
        selected = null;
        for (PlaybackCommands.Item item : readStoredItems()) if (item.id().equals(id)) selected = item;
        if (selected == null && page != LIST) page = LIST;
        binding.title.setText(state.getString("draft", selected == null ? "" : selected.title()));
        binding.command.setText(state.getString("command", selected == null ? "" : selected.command()));
    }

    private void onOperationResult(Result result) {
        if (result == null) return;
        operations.result.setValue(null);
        if (result.error != null) {
            Notify.show(Notify.getError(R.string.exo_command_error, result.error));
        } else {
            page = LIST;
            if (result.id != null) for (PlaybackCommands.Item item : readStoredItems()) if (item.id().equals(result.id)) selected = item;
        }
        render(true);
    }

    void start() {
    }

    void stop() {
    }

    private void onPrimary(View view) {
        if (operations.running) return;
        if (page == COMMAND) {
            saveCommand();
            return;
        }
        selected = null;
        editCommand("", "");
    }

    private void saveCommand() {
        String title = binding.title.getText().toString();
        String command = binding.command.getText().toString();
        if (!ExoCommand.isSupported(command)) {
            Notify.show(R.string.exo_command_unsupported);
            return;
        }
        PlaybackCommands.Item item = selected;
        String id = item == null ? null : item.id();
        runOperation(() -> {
            if (id == null) return PlaybackCommands.add(title, command).id();
            PlaybackCommands.update(id, title, command);
            return id;
        });
    }

    private boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
        if (actionId != EditorInfo.IME_ACTION_DONE || page != COMMAND) return false;
        onPrimary(view);
        return true;
    }

    private boolean isRoot() {
        return page == LIST;
    }

    private void back() {
        if (operations.running || isRoot()) return;
        page = page == COMMAND && selected != null ? EDIT : LIST;
        render(true);
    }

    private List<PlaybackCommands.Item> readStoredItems() {
        try {
            return PlaybackCommands.read().stream().filter(item -> ExoCommand.isSupported(item.command())).toList();
        } catch (JSONException e) {
            Notify.show(Notify.getError(R.string.exo_command_error, e));
            return List.of();
        }
    }

    private String getString(int id) {
        return owner.getString(id);
    }

    private void render(boolean focusContent) {
        if (binding == null || !owner.isAdded()) return;
        binding.heading.setText(getHeading());
        binding.primary.setVisibility(page == LIST || page == COMMAND ? View.VISIBLE : View.INVISIBLE);
        binding.primary.setNextFocusDownId(editingPage() ? R.id.title : View.NO_ID);
        binding.primary.setText(getPrimaryText());
        updateBusy();
        renderContent(focusContent);
    }

    private String getHeading() {
        return switch (page) {
            case EDIT -> selected.title();
            case COMMAND -> selected == null ? getString(R.string.command_add_command) : selected.title();
            default -> getString(R.string.exo_command_buttons);
        };
    }

    private int getPrimaryText() {
        return page == COMMAND ? R.string.dialog_positive : R.string.command_add;
    }

    private void renderContent(boolean focusContent) {
        updateItemSpacing();
        int topPadding = page == EDIT && !Util.isLeanback() ? 0 : owner.getResources().getDimensionPixelSize(R.dimen.playback_dialog_spacing);
        binding.items.setPaddingRelative(binding.items.getPaddingStart(), topPadding, binding.items.getPaddingEnd(), binding.items.getPaddingBottom());
        boolean editing = editingPage();
        if (!editing && binding.form.getVisibility() == View.VISIBLE) Util.hideKeyboard(binding.title);
        binding.form.setVisibility(editing ? View.VISIBLE : View.GONE);
        binding.commandInput.setVisibility(page == COMMAND ? View.VISIBLE : View.GONE);
        binding.items.setVisibility(editing ? View.GONE : View.VISIBLE);
        List<Row> rows = buildRows();
        binding.empty.setVisibility(isRoot() && rows.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.replaceRows(rows);
        if (editing) {
            binding.title.setImeOptions(page == COMMAND ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
            binding.title.requestFocus();
            binding.title.getContext().getSystemService(InputMethodManager.class).restartInput(binding.title);
        } else if (focusContent && Util.isLeanback()) {
            restoreFocus(rows);
        }
    }

    private void restoreFocus(List<Row> rows) {
        if (rows.isEmpty()) {
            binding.primary.requestFocus();
        } else {
            int position = Math.min(rows.size() - 1, page == LIST ? listPosition : editPosition);
            binding.items.getLayoutManager().scrollToPosition(position);
            OneShotPreDrawListener.add(binding.items, () -> focusItem(position));
        }
    }

    private void focusItem(int position) {
        if (binding == null) return;
        View view = binding.items.getLayoutManager().findViewByPosition(position);
        if (view != null) view.requestFocus();
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        actions.clear();
        if (page == LIST) addItemRows(rows);
        else if (page == EDIT) addEditRows(rows);
        return rows;
    }

    private void addItemRows(List<Row> rows) {
        items = readStoredItems();
        for (int i = 0; i < items.size(); i++) {
            PlaybackCommands.Item item = items.get(i);
            if (selected != null && item.id().equals(selected.id())) listPosition = i;
            rows.add(createItemRow(item));
        }
    }

    private Row createItemRow(PlaybackCommands.Item item) {
        String suffix = getString(item.enabled() ? R.string.command_enabled : R.string.command_disabled);
        if (item.hidden()) suffix += " · " + getString(R.string.command_hidden);
        return new Row(item.title(), suffix);
    }

    private void addEditRows(List<Row> rows) {
        actions.add(R.string.command_edit_command);
        actions.add(selected.hidden() ? R.string.command_show : R.string.command_hide);
        if (neighboringButton(true) != null) actions.add(R.string.command_move_earlier);
        if (neighboringButton(false) != null) actions.add(R.string.command_move_later);
        actions.add(selected.enabled() ? R.string.command_disable : R.string.command_enable);
        actions.add(R.string.command_delete);
        for (int actionResId : actions) rows.add(new Row(getString(actionResId), ""));
    }

    private void updateItemSpacing() {
        int spacing = owner.getResources().getInteger(page == EDIT ? R.integer.command_action_spacing : R.integer.command_item_spacing);
        if (itemSpacing == spacing) return;
        if (itemDecoration != null) binding.items.removeItemDecoration(itemDecoration);
        itemSpacing = spacing;
        itemDecoration = spacing == 0 ? null : new SpaceItemDecoration(1, spacing);
        if (itemDecoration != null) binding.items.addItemDecoration(itemDecoration);
    }

    private boolean editingPage() {
        return page == COMMAND;
    }

    private PlaybackCommands.Item neighboringButton(boolean moveEarlier) {
        List<PlaybackCommands.Item> buttons = readStoredItems();
        for (int i = 0; i < buttons.size(); i++) {
            if (!buttons.get(i).id().equals(selected.id())) continue;
            int neighborIndex = i + (moveEarlier ? -1 : 1);
            return neighborIndex >= 0 && neighborIndex < buttons.size() ? buttons.get(neighborIndex) : null;
        }
        return null;
    }

    private void editCommand(String title, String command) {
        binding.title.setText(title);
        binding.command.setText(command);
        page = COMMAND;
        render(true);
    }

    private void select(int position) {
        if (operations.running || position == RecyclerView.NO_POSITION) return;
        if (page == LIST) {
            listPosition = position;
            editPosition = 0;
            selected = items.get(position);
            page = EDIT;
            render(true);
        } else if (page == EDIT) {
            editPosition = position;
            selectAction(actions.get(position));
        }
    }

    private void selectAction(int actionResId) {
        PlaybackCommands.Item item = selected;
        if (actionResId == R.string.command_edit_command) {
            editCommand(item.title(), item.command());
        } else if (actionResId == R.string.command_delete) {
            runOperation(() -> {
                PlaybackCommands.delete(item.id());
                return null;
            });
        } else if (actionResId == R.string.command_hide || actionResId == R.string.command_show) {
            runOperation(() -> {
                PlaybackCommands.setHidden(item.id(), !item.hidden());
                return item.id();
            });
        } else if (actionResId == R.string.command_move_earlier || actionResId == R.string.command_move_later) {
            PlaybackCommands.Item neighbor = neighboringButton(actionResId == R.string.command_move_earlier);
            if (neighbor != null) runOperation(() -> {
                PlaybackCommands.move(item.id(), neighbor.id());
                return item.id();
            });
        } else if (actionResId == R.string.command_enable || actionResId == R.string.command_disable) {
            runOperation(() -> {
                PlaybackCommands.setEnabled(item.id(), !item.enabled());
                return item.id();
            });
        }
    }

    private record Row(String title, String detail) {
    }

    private final class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.Holder> {

        private List<Row> rows = new ArrayList<>();

        private void replaceRows(List<Row> rows) {
            int previousCount = this.rows.size();
            if (previousCount > 0) {
                this.rows = new ArrayList<>();
                notifyItemRangeRemoved(0, previousCount);
            }
            this.rows = rows;
            if (!rows.isEmpty()) notifyItemRangeInserted(0, rows.size());
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false));
        }

        @Override
        public int getItemViewType(int position) {
            return page == EDIT ? R.layout.adapter_command_action : R.layout.adapter_command_button;
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Row row = rows.get(position);
            holder.text.setText(row.title());
            if (holder.detail != null) {
                holder.detail.setText(row.detail());
                holder.detail.setVisibility(row.detail().isEmpty() ? View.GONE : View.VISIBLE);
            }
            holder.itemView.setEnabled(!operations.running);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        final class Holder extends RecyclerView.ViewHolder {

            private final TextView text;
            private final TextView detail;

            Holder(View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.text);
                detail = itemView.findViewById(R.id.detail);
                itemView.setOnClickListener(view -> select(getBindingAdapterPosition()));
            }
        }
    }

    private void runOperation(Operation operation) {
        operations.run(operation);
        updateBusy();
    }

    private void updateBusy() {
        if (binding == null) return;
        backCallback.setEnabled(operations.running || !isRoot());
        binding.primary.setEnabled(!operations.running);
        binding.items.setEnabled(!operations.running);
        for (int i = 0; i < binding.items.getChildCount(); i++) binding.items.getChildAt(i).setEnabled(!operations.running);
        binding.title.setEnabled(!operations.running);
        binding.command.setEnabled(!operations.running);
    }

    void save(Bundle state) {
        state.putInt("page", page);
        state.putString("selected", selected == null ? null : selected.id());
        state.putInt("listPosition", listPosition);
        state.putInt("editPosition", editPosition);
        if (binding != null) {
            state.putString("draft", binding.title.getText().toString());
            state.putString("command", binding.command.getText().toString());
        }
    }

    void release() {
        stop();
        backCallback.setEnabled(false);
        binding.items.setAdapter(null);
        binding = null;
        itemDecoration = null;
        itemSpacing = -1;
        adapter = null;
    }

    public static final class Operations extends ViewModel {

        private final MutableLiveData<Result> result = new MutableLiveData<>();
        private boolean running;

        private void run(Operation operation) {
            if (running) return;
            running = true;
            Task.execute(() -> {
                Result completed = execute(operation);
                App.post(() -> complete(completed));
            });
        }

        private Result execute(Operation operation) {
            Exception error = null;
            String id = null;
            try {
                id = operation.run();
            } catch (Exception e) {
                error = e;
            }
            return new Result(error, id);
        }

        private void complete(Result completed) {
            running = false;
            result.setValue(completed);
        }
    }

    private record Result(Exception error, String id) {
    }

    private interface Operation {

        String run() throws Exception;
    }
}
