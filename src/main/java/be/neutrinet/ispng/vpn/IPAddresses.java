/*
 * IPAddresses.java
 * Copyright (C) Apr 6, 2014 Wannes De Smet
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package be.neutrinet.ispng.vpn;

import be.neutrinet.ispng.VPN;
import com.googlecode.ipv6.IPv6Address;
import com.googlecode.ipv6.IPv6Network;
import com.googlecode.ipv6.IPv6NetworkMask;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.TableUtils;
import com.tufar.IPCalculator.IPv4;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.*;

/**
 * @author wannes
 */
public class IPAddresses {

    public static Dao<IPAddress, String> dao;

    static {
        Class cls = IPAddress.class;
        try {
            dao = DaoManager.createDao(VPN.cs, cls);
            TableUtils.createTableIfNotExists(VPN.cs, cls);

        } catch (SQLException ex) {
            Logger.getLogger(cls).error("Failed to create DAO", ex);
        }
    }

    public static Optional<IPAddress> findUnused(int ipVersion) {
        return findUnused(ipVersion, IPAddress.Purpose.CLIENT_ASSIGN);
    }

    public static Optional<IPAddress> findUnused(int ipVersion, String purpose) {
        try {
            QueryBuilder<IPAddress, String> queryBuilder = dao.queryBuilder();
            queryBuilder.limit(1L);
            queryBuilder.where().eq("client_id", -1).and()
                    .eq("ipVersion", ipVersion).and().eq("purpose", purpose);
            List<IPAddress> query = dao.query(queryBuilder.prepare());

            if (query.isEmpty()) {
                // Houston, we have a problem
                Logger.getLogger(IPAddresses.class).error("Ran out of IPv" + ipVersion + " addresses");
            } else {
                return Optional.ofNullable(query.get(0));
            }
        } catch (SQLException ex) {
            Logger.getLogger(IPAddresses.class).error("Failed to find IPv" + ipVersion + " address", ex);
        }

        return Optional.empty();
    }

    public static List<IPAddress> forUser(UUID userId, int ipVersion) {
        try {
            ArrayList<IPAddress> addrs = new ArrayList<>();
            List<Client> clients = Clients.dao.queryForEq("userId", UUID.fromString("" + userId));
            for (Client client : clients) {
                addrs.addAll(client.leases);
            }

            return addrs;
        } catch (SQLException ex) {
            Logger.getLogger(IPAddresses.class).error("Failed to find IP address", ex);
        }

        return null;
    }

    public static List<IPAddress> forClient(Client client, int ipVersion) {
        return forClient(client, ipVersion, IPAddress.Purpose.CLIENT_ASSIGN);
    }

    public static List<IPAddress> forClient(Client client, int ipVersion, String purpose) {
        try {
            QueryBuilder<IPAddress, String> queryBuilder = dao.queryBuilder();
            queryBuilder.where().eq("client_id", client.id).and()
                    .eq("ipVersion", ipVersion).and()
                    .eq("purpose", purpose);
            return dao.query(queryBuilder.prepare());
        } catch (SQLException ex) {
            Logger.getLogger(IPAddresses.class).error("Failed to find IP address", ex);
        }
        return new ArrayList<>();
    }

    public static List<IPAddress> addv4SubnetToPool(String subnetCIDR, String purpose) {
        ArrayList<IPAddress> addrs = new ArrayList<>();
        IPv4 subnet = new IPv4(subnetCIDR);
        try {
            IPAddresses.dao.callBatchTasks(() -> {
                for (String addr : subnet.getAvailableIPs(subnet.getNumberOfHosts().intValue())) {
                    try {
                        IPAddress ipa = new IPAddress();
                        ipa.address = addr;
                        ipa.ipVersion = 4;
                        ipa.netmask = 32;
                        ipa.purpose = purpose;
                        IPAddresses.dao.createIfNotExists(ipa);
                        addrs.add(ipa);
                    } catch (Exception ex) {
                        Logger.getLogger(IPAddresses.class).error("Failed to add IPv4 address to pool", ex);
                    }
                }
                return null;
            });
        } catch (Exception ex) {
            Logger.getLogger(IPAddresses.class).error("Failed to add subnet4 " + subnetCIDR + "to pool", ex);
        }

        return addrs;
    }

    public static List<IPAddress> addv6SubnetToPool(String subnetstr, String purpose) {
        ArrayList<IPAddress> addrs = new ArrayList<>();
        IPv6Network subnet = IPv6Network.fromString(subnetstr);
        try {
            IPAddresses.dao.callBatchTasks(() -> {
                for (IPv6Address addr : subnet) {
                    IPAddress ipa = new IPAddress();
                    ipa.address = addr.toString();
                    ipa.ipVersion = 6;
                    ipa.netmask = 128;
                    ipa.purpose = purpose;
                    IPAddresses.dao.createIfNotExists(ipa);
                    addrs.add(ipa);
                }
                return null;
            });
        } catch (Exception ex) {
            Logger.getLogger(IPAddresses.class).error("Failed to add subnet6 " + subnetstr + "to pool", ex);
        }

        return addrs;
    }

    public static List<IPAddress> addv6SubnetToPool(String subnetstr, String purpose, int prefix) {
        ArrayList<IPAddress> addrs = new ArrayList<>();
        IPv6Network subnet = IPv6Network.fromString(subnetstr);
        try {
            IPAddresses.dao.callBatchTasks(() -> {
                Iterator<IPv6Network> it = subnet.split(IPv6NetworkMask.fromPrefixLength(prefix));
                IPv6Network net = it.next(); // skip first zero address

                for (; it.hasNext(); ) {
                    net = it.next();

                    IPAddress ipa = new IPAddress();
                    ipa.address = net.toString().substring(0, net.toString().length() - ("" + prefix).length() - 1);
                    ipa.ipVersion = 6;
                    ipa.netmask = prefix;
                    ipa.purpose = purpose;
                    IPAddresses.dao.createIfNotExists(ipa);
                    addrs.add(ipa);
                }
                return null;
            });
        } catch (Exception ex) {
            Logger.getLogger(IPAddresses.class).error("Failed to add subnet6 " + subnetstr + "to pool", ex);
        }

        return addrs;
    }
}
