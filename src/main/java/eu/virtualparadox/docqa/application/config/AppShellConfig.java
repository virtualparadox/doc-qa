package eu.virtualparadox.docqa.application.config;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Global Vaadin app shell configuration.
 * Enables server push for the whole app.
 */
@Push(PushMode.AUTOMATIC)
@Theme(value = "docqa", variant = Lumo.DARK)
public final class AppShellConfig implements AppShellConfigurator {
}