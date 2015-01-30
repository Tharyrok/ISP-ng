package be.neutrinet.ispng.vpn;

import be.neutrinet.ispng.security.OwnedEntity;
import be.neutrinet.ispng.vpn.ip.SubnetLease;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.j256.ormlite.dao.ForeignCollection;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Created by wannes on 11/10/14.
 */
@DatabaseTable(tableName = "ovpn_clients")
public class Client implements OwnedEntity, Serializable {

    @DatabaseField(generatedId = true)
    public int id;
    @DatabaseField(canBeNull = false)
    public String commonName;
    @DatabaseField(canBeNull = false, foreign = true, foreignAutoRefresh = true)
    public User user;
    @ForeignCollectionField(foreignColumnName = "client")
    @JsonManagedReference
    public ForeignCollection<IPAddress> leases;
    @ForeignCollectionField(foreignColumnName = "client")
    @JsonManagedReference
    public ForeignCollection<SubnetLease> subnetLeases;
    @DatabaseField(defaultValue = "true")
    public boolean enabled;

    public static Optional<Client> match(be.neutrinet.ispng.openvpn.Client vpnClient) {
        try {
            List<User> users = Users.dao.queryForEq("email", vpnClient.username);

            assert users.size() == 1;

            User user = users.get(0);
            HashMap<String, Object> query = new HashMap<>();
            query.put("user_id", user.id);
            query.put("commonName", vpnClient.commonName);
            List<Client> clients = Clients.dao.queryForFieldValues(query);

            if (clients.size() > 1) {
                Logger.getLogger(Client.class).error("Multiple client definitions, user: " + vpnClient.username +
                ", commonName: " + vpnClient.commonName);
                return Optional.empty();
            }

            if (clients.isEmpty()) return Optional.empty();
            else return Optional.of(clients.get(0));
        } catch (Exception ex) {
            Logger.getLogger(Client.class).warn("Failed to match VPN client", ex);
        }

        return Optional.empty();
    }

    public static Client create(be.neutrinet.ispng.openvpn.Client client) {
        Client c = new Client();
        c.commonName = client.commonName;

        try {
            List<User> users = Users.dao.queryForEq("email", client.username);

            assert users.size() == 1;

            User user = users.get(0);
            c.user = user;

            Clients.dao.createIfNotExists(c);
        } catch (Exception ex) {
            Logger.getLogger(Client.class).error("Failed to create VPN client", ex);
        }

        return c;
    }

    public IPAddress getOrCreateInterconnectIP(int ipVersion) {
        IPAddress interconnect = null;
        List<IPAddress> interconnects = IPAddresses.forClient(this, ipVersion, IPAddress.Purpose.INTERCONNECT);
        if (interconnects.size() < 1) {
            interconnect = IPAddresses.findUnused(6, IPAddress.Purpose.INTERCONNECT).orElseThrow(() -> new IllegalStateException("No interconnect IPs available"));
        } else {
            interconnect = interconnects.get(0);
        }

        return interconnect;
    }

    @Override
    public boolean isOwnedBy(User user) {
        return this.user.equals(user);
    }
}
