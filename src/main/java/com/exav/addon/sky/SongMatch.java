package com.exav.addon.sky;

import java.util.Locale;
import net.anubis.client.module.impl.client.SpotifyHudModule;
import net.anubis.client.util.spotify.NowPlaying;

/** Works out what kind of song the Spotify HUD is playing. */
public final class SongMatch {
    private SongMatch() {
    }

    /** the song that's playing right now (null when paused / nothing / Spotify HUD off) */
    public static NowPlaying playing() {
        SpotifyHudModule spotify = SpotifyHudModule.active();
        NowPlaying now = spotify == null ? null : spotify.nowPlaying();
        return now != null && now.playing() ? now : null;
    }

    private static String letters(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static String words(String s) {
        return s == null ? "" : " " + s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    /** Yeat as the artist, or featured: "feat. Yeat" / "with Yeat" in the title, or listed among the artists */
    public static boolean yeat(NowPlaying now) {
        if (now == null) return false;
        return letters(now.artist()).contains("yeat") || words(now.title()).contains(" yeat ");
    }

    public static boolean icp(NowPlaying now) {
        if (now == null) return false;
        String a = letters(now.artist());
        return a.contains("insaneclownposse") || words(now.title()).contains(" insane clown posse ");
    }

    /** words and phrases that mean a Christmas song */
    private static final String[] CHRISTMAS = {
            " christmas ", " xmas ", " x mas ", " santa ", " santa claus ", " jingle ", " sleigh ", " mistletoe ",
            " reindeer ", " rudolph ", " noel ", " navidad ", " holly jolly ", " deck the halls ", " silent night ",
            " let it snow ", " winter wonderland ", " frosty the snowman ", " snowman ", " o holy night ",
            " joy to the world ", " away in a manger ", " little drummer boy ", " hark the herald ",
            " o come all ye faithful ", " we wish you a merry ", " carol of the bells ", " nutcracker ",
            " the first noel ", " o christmas tree ", " o tannenbaum ", " god rest ye ", " good king wenceslas ",
            " it s beginning to look ", " rockin around ", " rocking around ", " blue christmas ", " last christmas ",
            " white christmas ", " feliz navidad ", " jingle bell ", " here comes santa ", " up on the housetop ",
            " twelve days of christmas ", " o little town of bethlehem ", " what child is this ", " stille nacht ",
            " santa baby ", " snow is falling ", " underneath the tree ", " mary did you know ", " gingerbread ",
            " candy cane ", " north pole ", " elves ", " yuletide ", " yule ", " chestnuts roasting ",
    };

    public static boolean christmas(NowPlaying now) {
        if (now == null) return false;
        String text = words(now.title()) + words(now.album()) + words(now.artist());
        for (String w : CHRISTMAS) {
            if (text.contains(w)) return true;
        }
        return false;
    }
}
