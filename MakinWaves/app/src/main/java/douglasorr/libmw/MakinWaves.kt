package douglasorr.libmw;

object MakinWaves {
    init {
        System.loadLibrary("mw")
    }
    external fun hello(to: String): String
}
