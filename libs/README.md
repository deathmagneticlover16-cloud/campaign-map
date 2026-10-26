# Compile-only compatibility libraries

- `recruits.jar` is the Recruits API/runtime shape used by the optional group adapter.
- `immersive-paintings-0.6.13-api-stub.jar` contains only the two client type signatures needed to compile the guarded `ImmersivePaintingScreen` subclass. It is never packaged in Campaign Map. At runtime, Immersive Paintings 0.6.13 supplies the real screen, entity, browser, uploads, filters, previews, pagination, and tooltips.
