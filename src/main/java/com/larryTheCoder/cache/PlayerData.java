/*
 * Copyright (c) 2016-2020 larryTheCoder and contributors
 *
 * Permission is hereby granted to any persons and/or organizations
 * using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or
 * any derivatives of the work for commercial use or any other means to generate
 * income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing
 * and/or trademarking this software without explicit permission from larryTheCoder.
 *
 * Any persons and/or organizations using this software must disclose their
 * source code and have it publicly available, include this license,
 * provide sufficient credit to the original authors of the project (IE: larryTheCoder),
 * as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,FITNESS FOR A PARTICULAR
 * PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.larryTheCoder.cache;

import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.database.DatabaseManager;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import org.sql2o.Connection;
import org.sql2o.data.Row;
import org.sql2o.data.Table;

import java.util.*;

import static com.larryTheCoder.database.TableSet.*;

/**
 * @author larryTheCoder
 */
public class PlayerData implements Cloneable {

    // Player critical information data.
    @Getter
    public final String playerName;
    @Getter
    public final String playerXUID;

    @Setter @Getter
    public int resetLeft;

    @Setter @Getter
    public int islandLevel;

    private boolean challengeFetched = false;
    private final HashMap<String, Boolean> challengeList = new HashMap<>();
    private final HashMap<String, Integer> challengeListTimes = new HashMap<>();

    @Getter @Setter
    public String locale;
    @Getter
    public List<String> banList;

    private PlayerData(String playerName, String playerXUID, String pubLocale, List<String> banList, int resetAttempts, int islandLevels) {
        this.playerName = playerName;
        this.playerXUID = playerXUID;

        this.locale = pubLocale;
        this.banList = banList;
        this.resetLeft = resetAttempts;
        this.islandLevel = islandLevels;
    }

    public static PlayerData fromRows(Row dataRow) {
        return new PlayerData(
                dataRow.getString("playerName"),
                dataRow.getString("playerUUID"),
                dataRow.getString("locale"),
                Utils.stringToArray(dataRow.getString("banList"), ":"),
                dataRow.getInteger("resetAttempts"),
                dataRow.getInteger("islandLevels"));
    }

    /**
     * Checks if a challenge not exists in the player's challenge list
     *
     * @param challenge The challenge to be checked
     * @return true if challenge is listed in the player's challenge list,
     * otherwise false
     */
    public boolean challengeNotExists(final String challenge) {
        return !challengeList.containsKey(challenge.toLowerCase());
    }

    /**
     * Checks if a challenge is recorded as completed in the player's challenge
     * list or not
     *
     * @param challenge The challenge to be checked
     * @return true if the challenge is listed as complete, false if not
     */
    public boolean checkChallenge(final String challenge) {
        if (challengeList.containsKey(challenge.toLowerCase())) {
            return challengeList.get(challenge.toLowerCase());
        }
        return false;
    }

    /**
     * Checks how many times a challenge has been done
     *
     * @param challenge The challenge to be checked
     * @return number of times
     */
    public int checkChallengeTimes(String challenge) {
        if (challengeListTimes.containsKey(challenge.toLowerCase())) {
            //Utils.sendDebug("DEBUG: check " + challenge + ":" + challengeListTimes.get(challenge.toLowerCase()));
            return challengeListTimes.get(challenge.toLowerCase());
        }
        return 0;
    }

    /**
     * Map of all of the known challenges and how many times each
     * one has been completed. This is a view of the challenges
     * map that only allows read operations.
     *
     * @return The list of all the challenges times
     */
    public Map<String, Integer> getChallengeTimes() {
        if (!challengeFetched) {
            fetchChallengeBody();
        }

        return Collections.unmodifiableMap(challengeListTimes);
    }

    public Map<String, Boolean> getChallengeStatus() {
        if (!challengeFetched) {
            fetchChallengeBody();
        }

        return Collections.unmodifiableMap(challengeList);
    }

    @Override
    public String toString() {
        return String.format("PlayerData(playerName=%s, playerXUID=%s, resetLeft=%s, islandLevel=%s, locale=%s)",
                playerName, playerXUID, resetLeft, islandLevel, locale);
    }

    /**
     * Fetch challenges body of this player.
     * <p>
     * This body is not fetched by default, therefore you may have to execute
     * this first before fetching any challenges.
     */
    public void fetchChallengeBody() {
        if (challengeFetched) {
            return;
        }

        Connection connection = ASkyBlock.get().getDatabase().getConnection();

        Table data = connection.createQuery(FETCH_PLAYER_DATA.getQuery())
                .addParameter("playerName", playerName)
                .executeAndFetchTable();

        if (!data.rows().isEmpty()) {
            Row row = data.rows().get(0);

            encodeChallengeList(row.getString("challengesList"), row.getString("challengesTimes"));
        }

        challengeFetched = true;
    }

    /**
     * Records the challenge as being complete in the player's list. If the
     * challenge is not listed in the player's challenge list already, then it
     * will be added.
     *
     * @param challenge The challenge name
     */
    public void completeChallenge(final String challenge) {
        // plugin.getLogger().info("DEBUG: Complete challenge");
        challengeList.put(challenge.toLowerCase(), true);
        // Count how many times the challenge has been done
        int times = 0;
        if (challengeListTimes.containsKey(challenge.toLowerCase())) {
            times = challengeListTimes.get(challenge.toLowerCase());
        }
        times++;
        challengeListTimes.put(challenge.toLowerCase(), times);
        // Utils.sendDebug(decodeChallengeList("clt"));
        // plugin.getLogger().info("DEBUG: complete " + challenge + ":" +
        // challengeListTimes.get(challenge.toLowerCase()).intValue() );
    }

    /**
     * Resets a specific challenge.
     *
     * @param challenge the challenge name
     */
    public void resetChallenge(final String challenge) {
        //plugin.getLogger().info("DEBUG: reset challenge");
        challengeList.put(challenge, false);
        challengeListTimes.put(challenge, 0);
    }

    /**
     * Decode a challenge list that needs
     * to be saved into database got
     * a raw of data of it.
     *
     * @param type The type of the data needs to be decoded
     *             either its 'cl' or 'clt'
     * @return decoded data of the type
     */
    public String decodeChallengeList(String type) {
        StringBuilder buf = new StringBuilder();
        // Need to decode one of these
        if (type.equalsIgnoreCase("cl")) {
            challengeList.forEach((key, value) -> {
                if (buf.length() > 0) {
                    buf.append(", ");
                }
                buf.append(key).append(":").append(value ? "1" : "0");
            });
        } else if (type.equalsIgnoreCase("clt")) {
            challengeListTimes.forEach((key, value) -> {
                if (buf.length() > 0) {
                    buf.append(", ");
                }
                buf.append(key).append(":").append(value);
            });
        } else {
            Utils.send("&cUnknown challenge list: " + type + ", returning null...");
            buf.append("null");
        }
        return buf.toString();
    }

    private void encodeChallengeList(String challenges, String challengesTime) {
        setupChallengeList();
        try {
            // Challenges encode for PlayerData.challengeList
            String[] at = challenges.split(", ");
            for (String string : at) {
                String[] at2 = string.split(":");
                ArrayList<String> list = new ArrayList<>(Arrays.asList(at2));

                boolean value = list.get(1).equalsIgnoreCase("1");
                challengeList.put(list.get(0).toLowerCase(), value);
            }

            // Challenges encode for PlayerData.challengeListTimes
            at = challengesTime.split(", ");
            for (String string : at) {
                String[] at2 = string.split(":");
                ArrayList<String> list = new ArrayList<>(Arrays.asList(at2));

                challengeListTimes.put(list.get(0).toLowerCase(), Integer.parseInt(list.get(1)));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Prepare the challenge list for the
     * first time to to the user
     */
    private void setupChallengeList() {
        for (String challenges : Settings.challengeList) {
            challengeList.put(challenges, false);
            challengeListTimes.put(challenges, 0);
        }
    }

    /**
     * Save player data asynchronously.
     */
    public void saveData() {
        ASkyBlock.get().getDatabase().pushQuery(new DatabaseManager.DatabaseImpl() {
            @Override
            public void executeQuery(Connection connection) {
                connection.createQuery(PLAYER_UPDATE_MAIN.getQuery())
                        .addParameter("playerName", playerName)
                        .addParameter("playerUUID", playerXUID)
                        .addParameter("locale", locale)
                        .addParameter("banList", Utils.arrayToString(banList))
                        .addParameter("resetLeft", resetLeft)
                        .addParameter("islandLevels", islandLevel)
                        .executeUpdate();

                if (!challengeFetched) {
                    return;
                }

                connection.createQuery(PLAYER_UPDATE_DATA.getQuery())
                        .addParameter("playerName", playerName)
                        .addParameter("challengesList", decodeChallengeList("cl"))
                        .addParameter("challengesTimes", decodeChallengeList("clt"))
                        .executeUpdate();
            }
        });
    }
}
