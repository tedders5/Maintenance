/*
 * Maintenance - https://git.io/maintenancemode
 * Copyright (C) 2018-2021 KennyTV (https://github.com/KennyTV)
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

package eu.kennytv.maintenance.core.command.subcommand;

import eu.kennytv.maintenance.core.MaintenancePlugin;
import eu.kennytv.maintenance.core.command.CommandInfo;
import eu.kennytv.maintenance.core.util.DummySenderInfo;
import eu.kennytv.maintenance.core.util.SenderInfo;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

public final class WhitelistAddCommand extends CommandInfo {

    public WhitelistAddCommand(final MaintenancePlugin plugin) {
        super(plugin, "whitelist.add");
    }

    @Override
    public void execute(final SenderInfo sender, final String[] args) {
        if (args.length == 2) {
            if (args[1].length() == 36) {
                final UUID uuid = plugin.checkUUID(sender, args[1]);
                if (uuid != null) {
                    addPlayerToWhitelist(sender, uuid);
                }
            } else {
                addPlayerToWhitelist(sender, args[1]);
            }
        } else if (args.length == 3) {
            if (args[1].length() != 36) {
                sender.sendMessage(getHelpMessage());
                return;
            }

            final UUID uuid = plugin.checkUUID(sender, args[1]);
            if (uuid != null) {
                addPlayerToWhitelist(sender, new DummySenderInfo(uuid, args[2]));
            }
        } else {
            sender.sendMessage(getHelpMessage());
        }
    }

    @Override
    public List<String> getTabCompletion(final SenderInfo sender, final String[] args) {
        return args.length == 2 ? plugin.getCommandManager().getPlayersCompletion() : Collections.emptyList();
    }

    private void addPlayerToWhitelist(final SenderInfo sender, final String name) {
        final SenderInfo selected = plugin.getOfflinePlayer(name);
        if (selected == null) {
          //  sender.sendMessage(getMessage("playerNotOnline"));
        	String stringUuid = getUuidFromMojang(sender, name);
        	if (stringUuid == null) return;
        	UUID uuid = UUID.fromString(stringUuid);
        	addPlayerToWhitelist(sender, new DummySenderInfo(uuid, name));
            return;
        }

        addPlayerToWhitelist(sender, selected);
    }

    private void addPlayerToWhitelist(final SenderInfo sender, final UUID uuid) {
        final SenderInfo selected = plugin.getOfflinePlayer(uuid);
        if (selected == null) {
            sender.sendMessage(getMessage("playerNotFoundUuid"));
            return;
        }

        addPlayerToWhitelist(sender, selected);
    }

    private void addPlayerToWhitelist(final SenderInfo sender, final SenderInfo selected) {
        if (getSettings().addWhitelistedPlayer(selected.getUuid(), selected.getName())) {
            sender.sendMessage(getMessage("whitelistAdded").replace("%PLAYER%", selected.getName()));
        } else {
            sender.sendMessage(getMessage("whitelistAlreadyAdded").replace("%PLAYER%", selected.getName()));
        }
    }
    
    /**
     * returns the player UUID
     * @param sender
     * @param name
     * @return player UUID as a String, null if not found
     */
    private String getUuidFromMojang(SenderInfo sender, String name) {
        String url = "https://api.mojang.com/users/profiles/minecraft/" + name;
        try {
            @SuppressWarnings("deprecation")
			String UUIDJson = IOUtils.toString(new URL(url));
            if (UUIDJson.isEmpty()) {
                sender.sendMessage(plugin.getPrefix() 
                        + "§7Error: No such player found using Mojang's API. Is the service down?");
                return null;
            }
            JSONObject UUIDObject = (JSONObject) JSONValue.parseWithException(UUIDJson);
            return UUID.fromString(UUIDObject.get("id").toString().replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"))
                    .toString();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        sender.sendMessage(plugin.getPrefix() 
                + "§7Error: No such player exists in the database.");
        return null;
    }
}
