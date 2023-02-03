package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.handlers.AudioHandler;
import eu.nurkert.porticlegun.portals.PorticlePortal;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import javax.xml.bind.helpers.ValidationEventLocatorImpl;
import java.util.ArrayList;

public class TeleportationHandler implements Listener {

    @EventHandler
    public void on(PlayerMoveEvent event) {
        // check if the player moved to a different block
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            Player player = event.getPlayer();
            ArrayList<PorticlePortal> portals = OpenedPortalsHandler.getRelevantPortals(player);
            for (PorticlePortal portal : portals) {
                if (portal.getLocation().equals(event.getTo().getBlock().getLocation()) || (portal.getDirection().getY() == -1.0 && portal.getLocation().equals(event.getTo().getBlock().getLocation().add(0, 1, 0)))) {
                    Location destination = portal.getLinkedPortal().getLocation().clone().add(0.5, portal.getLinkedPortal().getDirection().getY() == -1.0 ? -1 : 0, 0.5);
                    destination.setDirection(portal.getLinkedPortal().getDirection());
                    Vector velocity = player.getVelocity();
                    player.teleport(destination);
                    player.setVelocity(destination.getDirection().multiply(velocity.length()));
                    break;
                }
            }
        }

    }

    double[][] expected = {
            new double[]{26d, 12d, 43d, 12d},
            new double[]{17d, 10d, 30d, 17d},
            new double[]{36d, 16d, 59d, 14d}
    };

    /**
     * Multiplies two matrices.
     *
     * @param firstMatrix  the first matrix
     * @param secondMatrix the second matrix
     * @return the result of the multiplication
     */
    double[][] multiplyMatrices(double[][] firstMatrix, double[][] secondMatrix) {
        double[][] result = new double[firstMatrix.length][secondMatrix[0].length];

        for (int row = 0; row < result.length; row++) {
            for (int col = 0; col < result[row].length; col++) {
                result[row][col] = multiplyMatricesCell(firstMatrix, secondMatrix, row, col);
            }
        }

        return result;
    }

    /**
     * Multiplies a cell of the result matrix.
     *
     * @param firstMatrix  the first matrix
     * @param secondMatrix the second matrix
     * @param row          the row of the cell
     * @param col          the column of the cell
     * @return the result of the multiplication
     */
    double multiplyMatricesCell(double[][] firstMatrix, double[][] secondMatrix, int row, int col) {
        double cell = 0;
        for (int i = 0; i < secondMatrix.length; i++) {
            cell += firstMatrix[row][i] * secondMatrix[i][col];
        }
        return cell;
    }
}
