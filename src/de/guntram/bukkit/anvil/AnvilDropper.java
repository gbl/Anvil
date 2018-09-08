package de.guntram.bukkit.anvil;

import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AnvilDropper implements Runnable {
    private final UUID playerUUID;

    long ticksSinceInception;
    private final long ticksToThrowAnvil;
    private final long ticksToThawPlayer;
    private final long ticksToRemoveAnvil;
    private final Anvil plugin;
    private final String message;
    private final String senderName;
    private boolean anvilThrown;
    private boolean playerThawed;
    private boolean anvilRemoved;
    private boolean waitingForFreeSpace;
    
    private Location playerPosition;
    private Location playerPositionLookingUp;
    
    private static String freezeMessage, throwMessage, thawMessage, removeMessage;
    private static int anvilHeight;
    private static boolean delayIfNoFreeSpace;
    
    public static void readConfigVars(Configuration config) {
            anvilHeight=config.getInt("anvilheight", 5);
            freezeMessage=config.getString("message.freeze");
            throwMessage =config.getString("message.anvil");
            thawMessage  =config.getString("message.thaw");
            removeMessage=config.getString("message.remove");
            delayIfNoFreeSpace=config.getBoolean("delayifnofreespace", false);
    }
    
    public String getMessage() { return message; }
    
    private boolean isAppropriateForAnvilDrop(Location location) {
        World world=playerPosition.getWorld();
        for (int i=0; i<=anvilHeight; i++) {
            if (world.getBlockAt(playerPosition.getBlockX(),
                                 playerPosition.getBlockY()+i,
                                 playerPosition.getBlockZ()).getType() != Material.AIR) {
                return false;
            }
        }
        
        Material below=
                world.getBlockAt(playerPosition.getBlockX(),
                                 playerPosition.getBlockY()-1,
                                 playerPosition.getBlockZ()).getType();
        return below.isOccluding();
    }
    
    @SuppressWarnings("LeakingThisInConstructor")
    public AnvilDropper(String senderName, String playerName, Anvil plugin, Configuration config)
                                        throws CannotDropAnvilException {
        
        if (anvilHeight==0) {
            readConfigVars(config);
        }
        Player player=Bukkit.getPlayer(playerName);
        if (player==null) {
            throw new CannotDropAnvilException("No player with that name: "+playerName);
        }
        this.senderName=senderName;
        playerUUID=player.getUniqueId();
        ticksSinceInception=0;
        ticksToThrowAnvil=config.getInt("ticks.freezetoanvil", 100);
        ticksToThawPlayer=config.getInt("ticks.freezetothaw" , 200);
        ticksToRemoveAnvil=config.getInt("ticks.freezetoremove" , 150);
        anvilThrown=false;
        
        this.plugin=plugin;
        playerPosition=player.getLocation();

        if (!isAppropriateForAnvilDrop(playerPosition)) {
            if (delayIfNoFreeSpace) {
                waitingForFreeSpace=true;
                message="Will drop an anvil on "+playerName+" as soon as they move to air";
            } else {
                throw new CannotDropAnvilException("No free space above "+playerName);                
            }
        } else {
            message="Dropping an anvil on "+playerName;
            startAnvilSequence(player);
        }
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 1);
    }
    
    private void startAnvilSequence(Player player) {
        playerPositionLookingUp=playerPosition.clone();
        playerPositionLookingUp.setPitch(-90);
        playerPositionLookingUp.setX((float)playerPositionLookingUp.getBlockX()+0.5);
        playerPositionLookingUp.setY((float)playerPositionLookingUp.getBlockY());
        playerPositionLookingUp.setZ((float)playerPositionLookingUp.getBlockZ()+0.5);
        if (freezeMessage!=null) 
            player.sendMessage(freezeMessage);
        plugin.addSpawnPreventLocation(playerPositionLookingUp);
        
    }
    
    @Override
    public void run() {
        Player player=Bukkit.getPlayer(playerUUID);
        if (player==null) {
            if (playerPositionLookingUp!=null)
                plugin.removeSpawnPreventLocation(playerPositionLookingUp);
            // If the player logged out, don't reschedule.
            return;
        }
        
        if (waitingForFreeSpace) {
            if (!isAppropriateForAnvilDrop(playerPosition=player.getLocation())) {
                // While the player is moving, don't retry on each tick - every 5th is sufficient
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 5);
                return;
            }
            waitingForFreeSpace=false;
            ticksSinceInception=0;
            startAnvilSequence(player);
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 1);
            return;
        }

        ticksSinceInception++;
        if (!anvilThrown && ticksSinceInception > ticksToThrowAnvil) {
            anvilThrown=true;
            ItemStack anvil=new ItemStack(Material.ANVIL, 1);
            // Unfortunately, setting the name of the item doesn't set the name of the (falling) block.
            // Try with /give Giselbaer minecraft:anvil{display:{Name:"{\"text\":\"Punisher\"}"}} 1, 
            // then placing that somewhere and breaking it.
            //ItemMeta meta = anvil.getItemMeta();
            //meta.setDisplayName(senderName + "'s Punishment");
            //anvil.setItemMeta(meta);
            Location anvilPos=playerPositionLookingUp.clone().add(0, anvilHeight, 0);
            anvilPos.setPitch(0);
            anvilPos.setYaw(0);
            anvilPos.getWorld().getBlockAt(anvilPos).setType(Material.ANVIL);
            if (throwMessage!=null) 
                player.sendMessage(throwMessage);
        }
        if (!playerThawed && ticksSinceInception > ticksToThawPlayer) {
            playerThawed=true;
            if (thawMessage!=null)
                player.sendMessage(thawMessage);
            player.teleport(playerPosition);
        }
        if (!playerThawed) {
            if (anvilThrown && !anvilRemoved) {
                // This doesn't work, seems like currentPosition isn't updated with
                // a possibly changed pitch/yaw at this point. But as we can't
                // set a block display name anyway ...
                Location currentPosition = player.getLocation();
                playerPositionLookingUp.setYaw(currentPosition.getYaw());
                playerPositionLookingUp.setPitch(currentPosition.getPitch());
            }
            player.teleport(playerPositionLookingUp);
        }
        
        if (!anvilRemoved && ticksSinceInception > ticksToRemoveAnvil) {
            anvilRemoved=true;
            // System.out.println("trying to remove anvil at "+playerPositionLookingUp.toString());
            Block block = playerPositionLookingUp.getWorld().getBlockAt(playerPositionLookingUp);
            Material mat=block.getType();
            if (mat==Material.ANVIL || mat==Material.CHIPPED_ANVIL || mat==Material.DAMAGED_ANVIL) {
                block.setType(Material.AIR);
                if (removeMessage!=null)
                    player.sendMessage(removeMessage);
            } else {
                plugin.getLogger().log(Level.WARNING, "Block to remove that should have been an anvil had material {0}", block.getType().getKey().getKey());
            }
        }

        if (!playerThawed || !anvilRemoved) {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 1);
        } else {
            plugin.removeSpawnPreventLocation(playerPositionLookingUp);
        }
    }
}
