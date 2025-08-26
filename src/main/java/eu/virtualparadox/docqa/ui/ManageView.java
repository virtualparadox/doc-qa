package eu.virtualparadox.docqa.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.SucceededEvent;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import eu.virtualparadox.docqa.catalog.entity.DocumentEntity;
import eu.virtualparadox.docqa.ingest.lifecycle.DocumentLifecycleManager;
import eu.virtualparadox.docqa.ingest.lifecycle.DocumentProcessorTracker;
import eu.virtualparadox.docqa.ingest.lifecycle.ProgressStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Manage documents view:
 * <ul>
 *   <li>Upload and queue documents for ingestion</li>
 *   <li>Track ingestion progress</li>
 *   <li>Delete selected documents</li>
 *   <li>Browse catalog entries in a grid</li>
 * </ul>
 * <p>
 * Push updates are supported via {@link UI#access(Runnable)} with a
 * progress callback from {@link DocumentProcessorTracker}.
 */
@Route("manage")
@UIScope
@SpringComponent
@RequiredArgsConstructor
public final class ManageView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(ManageView.class);

    private static final String MSG_INGESTED = "Queued for ingestion: ";
    private static final String MSG_FAILED = "Failed: ";

    private final DocumentLifecycleManager documentLifecycleManager;
    private final DocumentProcessorTracker documentProcessorTracker;

    private UI uiRef;

    // UI components
    private final ProgressBar totalProgress = new ProgressBar();
    private final ProgressBar currentProgress = new ProgressBar();
    private final MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
    private final Upload upload = new Upload(buffer);
    private final Grid<DocumentEntity> grid = new Grid<>(DocumentEntity.class, false);
    private final Button deleteBtn = new Button("Delete Selected");

    @PostConstruct
    private void initView() {
        setSizeFull();
        setSpacing(true);
        setPadding(true);

        configureProgressBars();
        configureUpload();
        configureGrid();
        configureDeleteButton();
        initializeProgressFromTracker();

        final H2 header = new H2("Manage Documents");

        add(header,
                totalProgress,
                currentProgress,
                upload,
                grid,
                new HorizontalLayout(deleteBtn));

        addAttachListener(event -> {
            uiRef = event.getUI();
            wireProgressCallback();
        });

        addDetachListener(event -> {
            documentProcessorTracker.setProgressCallback(null);
            uiRef = null;
        });
    }

    /**
     * Configure progress bars for ingestion tracking.
     */
    private void configureProgressBars() {
        totalProgress.setMin(0);
        totalProgress.setMax(100);
        currentProgress.setMin(0);
        currentProgress.setMax(100);

        totalProgress.setVisible(false);
        currentProgress.setVisible(false);
    }

    /**
     * Configure upload to accept multiple files and queue them for ingestion.
     */
    private void configureUpload() {
        upload.setDropAllowed(false);
        upload.setMaxFiles(10);
        upload.setAutoUpload(true);

        final List<SucceededEvent> succeededEvents = new ArrayList<>();
        upload.addSucceededListener(succeededEvents::add);

        upload.addStartedListener(e -> {
            totalProgress.setVisible(true);
            currentProgress.setVisible(true);
        });

        upload.addFinishedListener(e -> handleFinishedUploads(succeededEvents));
    }

    /**
     * Configure the document grid, including status column.
     */
    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(DocumentEntity::getId).setHeader("ID").setAutoWidth(true);
        grid.addColumn(DocumentEntity::getTitle).setHeader("Title").setFlexGrow(1);
        grid.addColumn(DocumentEntity::getMime).setHeader("MIME").setAutoWidth(true);
        grid.addColumn(doc -> doc.getStatus() != null ? doc.getStatus().name() : "UNKNOWN")
                .setHeader("Status")
                .setAutoWidth(true);

        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        grid.setHeight("400px");

        refresh();
    }

    /**
     * Configure delete button for selected documents.
     */
    private void configureDeleteButton() {
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        deleteBtn.addClickListener(e -> handleDelete());
    }

    /**
     * Handle finished uploads, queue them for ingestion.
     */
    private void handleFinishedUploads(final List<SucceededEvent> succeededEvents) {
        for (final SucceededEvent ev : succeededEvents) {
            try (InputStream is = buffer.getInputStream(ev.getFileName())) {
                final DocumentEntity saved =
                        documentLifecycleManager.saveAsync(ev.getFileName(), ev.getMIMEType(), is);
                Notification.show(MSG_INGESTED + saved.getTitle());
            } catch (Exception ex) {
                log.error("Upload failed", ex);
                Notification.show(MSG_FAILED + ex.getMessage(),
                        5000, Notification.Position.MIDDLE);
            }
        }
        succeededEvents.clear();
        upload.clearFileList();
        refresh();
    }

    /**
     * Handle deletion of selected documents.
     */
    private void handleDelete() {
        final List<DocumentEntity> selected = new ArrayList<>(grid.getSelectedItems());
        for (final DocumentEntity doc : selected) {
            try {
                documentLifecycleManager.deleteDocument(doc.getId());
            } catch (Exception ex) {
                Notification.show(MSG_FAILED + ex.getMessage(),
                        5000, Notification.Position.MIDDLE);
            }
        }
        refresh();
    }

    /**
     * Refresh grid contents from catalog.
     */
    private void refresh() {
        final List<DocumentEntity> items = documentLifecycleManager.listAll();
        log.info("Loaded {} documents", items.size());
        grid.setItems(items);
    }

    private void initializeProgressFromTracker() {
        final ProgressStatus status = documentProcessorTracker.getProgressStatus();
        applyProgress(status);
    }

    private void wireProgressCallback() {
        documentProcessorTracker.setProgressCallback((totalPercent, docPercent) -> {
            final UI ui = uiRef;
            if (ui == null) {
                return; // detached
            }
            ui.access(() -> {
                applyProgress(new ProgressStatus(totalPercent, docPercent));
                grid.getDataProvider().refreshAll();
            });
        });
    }

    private void applyProgress(final ProgressStatus status) {
        final int total = Math.clamp(status.totalPercent(), 0, 100);
        final int doc = Math.clamp(status.documentPercent(), 0, 100);

        final boolean active = total > 0 && total < 100;

        totalProgress.setValue(total);
        currentProgress.setValue(doc);

        totalProgress.setVisible(active);
        currentProgress.setVisible(active);
    }
}
