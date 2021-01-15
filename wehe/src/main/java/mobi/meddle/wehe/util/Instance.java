package mobi.meddle.wehe.util;

import androidx.annotation.NonNull;

/**
 * Class that is used to store details of one server instance
 *
 * @author rajesh
 */
class Instance {
    private String name;
    private String username;
    private String ssh_key;

    Instance(String name, String username, String ssh_key) {
        this.name = name;
        this.username = username;
        this.ssh_key = ssh_key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSsh_key() {
        return ssh_key;
    }

    public void setSsh_key(String ssh_key) {
        this.ssh_key = ssh_key;
    }

    @NonNull
    @Override
    public String toString() {
        return "Instance [name=" + name + ", username=" + username
                + ", ssh_key=" + ssh_key + "]";
    }
}
