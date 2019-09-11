package mobi.meddle.wehe.util;

/**
 * This interface provides methods that can be implemented by ReplayActivity
 *
 * @author rajesh
 */
public interface ReplayCompleteListener {

    /**
     * This is called when App is done processing replay on Open channel.
     * 1) Connects to VPN
     * 2) Starts scheduling Replay on VPN
     *
     * @param success
     */
    void openFinishCompleteCallback(Boolean success);

    /**
     * This is called when App is done processing replay on VPN channel.
     * 1) Disconnects to VPN
     * 2) Get Results (Not implemented yet)
     * 3) Starts scheduling Replay for next app
     *
     * @param success
     */

    void randomFinishCompleteCallback(Boolean success);
}
