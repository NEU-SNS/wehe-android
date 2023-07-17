package mobi.meddle.wehe.constant;

public class Consts {
    public static final String APPS_FILENAME = "apps_list.json";
    public static final String CONFIG_FILE = "configuration.properties";

    public static final String DEFAULT_SERVER = "wehe4.meddle.mobi";
    public static final String METADATA_SERVER = "wehe-metadata.meddle.mobi";
    public static final String MLAB_SERVERS = "https://locate.measurementlab.net/v2/nearest/wehe/replay";
    public static final String MLAB_WEB_SOCKET_SERVER_KEY = "wss://:4443/v0/envelope/access";
    public static final int NUM_LOCALIZATION_TESTS = 2;

    public static final String ARCEP_URL = "https://jalerte.arcep.fr/jalerte/?2";

    public static final int A_THRESHOLD = 50; //percent
    public static final int KS2PVAL_THRESHOLD = 1; //percent
    public static final String LOC_XPUT_PAIR_VS_SINGLE= "pairsum_vs_single_xput";
    public static final String LOC_LOSS_CORR = "loss_correlation";
    public static final int CORR_PVAL_THRESHOLD = 5; //percent
    public static final int CORR_RATIO_THRESHOLD = 95; //percent

    public static final boolean TIMEOUT_ENABLED = true;
    public static final int REPLAY_APP_TIMEOUT = 45; //in seconds
    public static final int REPLAY_PORT_TIMEOUT = 30; //in seconds
}
