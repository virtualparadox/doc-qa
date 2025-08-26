package eu.virtualparadox.docqa.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import com.vaadin.flow.component.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Main application view with tab navigation.
 * <p>
 * Loads subviews (Ask, Catalog, Manage) provided by Spring.
 */
@Route("")
@UIScope
@SpringComponent
@RequiredArgsConstructor
public final class MainView extends AppLayout {

    private final AskView askView;
    private final ManageView manageView;

    private final Map<Tab, Component> contents = new HashMap<>();
    private final VerticalLayout contentArea = new VerticalLayout();

    @PostConstruct
    public void initView() {
        setPrimarySection(Section.NAVBAR);

        H1 title = new H1("Document Q:A");
        title.getStyle().set("margin", "var(--lumo-space-m)");

        Tab askTab = new Tab("Ask");
        Tab manageTab = new Tab("Manage");

        contents.put(askTab, askView);
        contents.put(manageTab, manageView);

        Tabs tabs = new Tabs(askTab, manageTab);
        tabs.setSelectedTab(askTab);
        tabs.addSelectedChangeListener(e -> switchContent(tabs.getSelectedTab()));

        addToNavbar(title, tabs);
        setContent(contentArea);

        // initial content
        switchContent(askTab);
    }

    private void switchContent(final Tab selected) {
        contentArea.removeAll();
        Component content = contents.get(selected);
        if (content != null) {
            contentArea.add(content);
        }
    }
}
