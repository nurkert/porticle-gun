package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.portals.Portal;

public class GunColors {
    PortalColor primary;
    PortalColor secondary;

    public GunColors(PortalColor primary, PortalColor secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    public PortalColor getPrimary() {
        return primary;
    }

    public PortalColor getSecondary() {
        return secondary;
    }
    public PortalColor get(Portal.PortalType type) {
        if(type == Portal.PortalType.PRIMARY)
            return primary;
        return secondary;
    }
    private void setPrimary(PortalColor primary, String gunID) {
        this.primary = primary;
    }

    private void setSecondary(PortalColor secondary, String gunID) {
        this.secondary = secondary;
    }

    public void selectNextPrimary(String gunID) {
        setPrimary(primary.next(), gunID);
    }

    public void selectNextSecondary(String gunID) {
        setSecondary(secondary.next(), gunID);
    }

    public void selectPreviousPrimary(String gunID) {
        setPrimary(primary.previous(), gunID);
    }

    public void selectPreviousSecondary(String gunID) {
        setSecondary(secondary.previous(), gunID);
    }
}
