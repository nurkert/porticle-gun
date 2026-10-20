# Localization

All player-facing texts live in `plugins/PorticleGun/messages.yml`, which is copied on the first start. English (`en`)
and German (`de`) are included.

## Editing texts

- `default-language` is the fallback language and the one used for console output.
- Every language key (`en`, `de`, ...) has the same structure. Copy a section to add a language.
- Colours use `&` codes; placeholders look like `%gun_id%`, `%player%` or `%label%`.
- Run `/porticlegun reload` after editing.

## Language per player

Each player gets the language that matches their Minecraft locale (for example `de_de` uses `de`), otherwise the
default language. Other plugins can override it:

```java
MessageManager.setPlayerLanguage(player.getUniqueId(), "de"); // force German
MessageManager.setPlayerLanguage(player.getUniqueId(), null); // back to automatic
```
