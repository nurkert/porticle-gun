package eu.nurkert.porticlegun.rift;

import java.util.List;
import java.util.function.Consumer;

/**
 * A window asking for one line of text (a home's name), shown natively like {@link RiftScreen}.
 *
 * @param title the window title
 * @param body the lines above the text field
 * @param label the text field's label
 * @param initial the text the field starts with
 * @param maxLength how many characters fit
 * @param confirm the label of the confirm button (Java)
 * @param cancel the label of the cancel button (Java)
 * @param submit what happens with the entered text; it runs on the main thread
 * @param cancelled what happens when the window is cancelled (Bedrock: closed); it runs on the main thread
 */
record RiftPrompt(
        String title,
        List<String> body,
        String label,
        String initial,
        int maxLength,
        String confirm,
        String cancel,
        Consumer<String> submit,
        Runnable cancelled) {}
