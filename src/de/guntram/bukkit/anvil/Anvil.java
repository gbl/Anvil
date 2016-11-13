package de.guntram.bukkit.anvil;

import java.util.ArrayList;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;


public class Anvil extends JavaPlugin implements Listener {

    private Logger logger;
    private FileConfiguration config;
    
    private static final int ticksPerSecond=20;
    
    private ArrayList<Location> anvilSpawnPreventLocations;

    @Override
    public void onEnable() {

        logger=getLogger();
        saveDefaultConfig();
        
        config=getConfig();
        getServer().getPluginManager().registerEvents(this, this);
        anvilSpawnPreventLocations=new ArrayList<>();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName=command.getName();
        if (commandName.equalsIgnoreCase("anvil")) {
        
            if (args.length==1) {
                String message;
                if (sender.hasPermission("anvil.drop")) {
                    try {
                        AnvilDropper dropper=new AnvilDropper(args[0], this, config);
                        message=dropper.getMessage();
                    } catch (CannotDropAnvilException ex) {
                        message=ex.getMessage();
                    }
                } else {
                    message="You're lacking the anvil.drop permission";
                }
                sender.sendMessage(message);
                return true;
            }
            if (args.length==0)
                sender.sendMessage("Give a player name");
        }
        return false;
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getItemStack().getType()==Material.ANVIL) {
            Location loc=event.getLocation();
            if (isSpawnPreventLocation(loc)) {
                // Bukkit.broadcastMessage("Prevented Anvil spawn at "+loc.getX()+"/"+loc.getY()+"/"+loc.getZ());
                event.setCancelled(true);
            } else {
                // Bukkit.broadcastMessage("Allowed Anvil spawn at "+loc.getX()+"/"+loc.getY()+"/"+loc.getZ());
            }
        } else {
//            Bukkit.broadcastMessage("ItemSpawn "+event.getEntity().getItemStack().getType());
        }
    }
    
    public boolean isSpawnPreventLocation(Location loc) {
        for (Location tmp:anvilSpawnPreventLocations) {
            if (tmp.getBlockX()==loc.getBlockX()
            &&  (tmp.getBlockY()==loc.getBlockY() || tmp.getBlockY()==loc.getBlockY()+1)
            &&  tmp.getBlockZ()==loc.getBlockZ()) {
                return true;
            }
        }
        return false;
    }
    
    public void addSpawnPreventLocation(Location loc) {
        // Bukkit.broadcastMessage("Add prevent anvil spawn at "+loc.getBlockX()+"/"+loc.getBlockY()+"/"+loc.getBlockZ());
        anvilSpawnPreventLocations.add(loc);
    }
    
    public void removeSpawnPreventLocation(Location loc) {
        // Bah, this is a quick and ugly hack to avoid ConcurrentModificationExceptions
        // Bukkit.broadcastMessage("Remove prevent anvil spawn at "+loc.getBlockX()+"/"+loc.getBlockY()+"/"+loc.getBlockZ());
        ArrayList tempList=new ArrayList<Location>();
        for (Location tmp:anvilSpawnPreventLocations) {
            if (tmp.getBlockX()==loc.getBlockX()
            &&  tmp.getBlockY()==loc.getBlockY()
            &&  tmp.getBlockZ()==loc.getBlockZ()) {
                // remove it ... anvilSpawnPreventLocations.remove(tmp);
            } else {
                tempList.add(tmp);
            }
        }
        anvilSpawnPreventLocations=tempList;
    }
}
