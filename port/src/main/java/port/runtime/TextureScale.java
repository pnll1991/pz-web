package port.runtime;

import org.teavm.jso.JSBody;

/**
 * Ronda 9X: texturas 1x opt-in (?tile1x=1) para achicar la carga inicial.
 *
 * La copia fijada hardcodea el escala 2x: {@code Core.getOptionTexture2x()} devuelve {@code true} siempre y el
 * {@code <clinit>} de {@code zombie.core.Core} fija {@code tileScale=2} (verificado con {@code javap -c} sobre el
 * jar fijado). {@code zombie.GameWindow#enter()} elige los packs de textura leyendo {@code Core.tileScale} antes
 * de montar ninguno, así que {@code port.compiler.TextureScaleCompatibility} inyecta al inicio de ese método
 * {@code Core.tileScale = tile1x()} ({@code 1} con el flag, {@code 2} sin él). Las fuentes del port no compilan contra el jar (como PlayerProbe); el
 * único contacto con clases del juego lo pone el transformer con IR directa.
 *
 * El patrón del repo para llamadas JS inyectadas (ver StartupProgress.sideEffect) es un método Java común que
 * reenvía a un {@code @JSBody}: por eso {@link #tile1x()} envuelve al nativo y el transformer llama a él.
 *
 * Apagado (el default) se asigna {@code 2}, el valor que el {@code <clinit>} del juego ya pone. Encendido el juego monta
 * Tiles1x/Tiles1x.floor/Overlays1x/JumboTrees1x (49 MB) en vez de Tiles2x (321 MB), y packs.mjs baja solo el
 * conjunto activo. No cambia reglas del juego: solo qué texturas de tiles se cargan, que en el jar original
 * existe (la opción 2x de usuario fue desactivada por el juego, no eliminada).
 */
public final class TextureScale {
    private TextureScale() { }

    @JSBody(script = "return (globalThis.pzRuntime && globalThis.pzRuntime.tile1x) ? 1 : 2;")
    private static native int tile1xRequested();

    public static int tile1x() {
        return tile1xRequested();
    }

    /** Cuerpo vacío por diseño: {@code TextureScaleCompatibility} lo reescribe con IR (setstatic sobre {@code Core.tileScale}). */
    public static void apply() { }
}
