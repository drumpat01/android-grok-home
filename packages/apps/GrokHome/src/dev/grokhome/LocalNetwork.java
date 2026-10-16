package dev.grokhome;

/** Literal RFC1918 IPv4 addresses only; never resolves a public hostname. */
final class LocalNetwork {
    static boolean isPrivateIpv4(String host){
        String[] parts=host.split("\\.",-1);if(parts.length!=4)return false;
        int[] octets=new int[4];
        for(int i=0;i<4;i++){
            if(!parts[i].matches("[0-9]{1,3}"))return false;
            octets[i]=Integer.parseInt(parts[i]);if(octets[i]>255)return false;
        }
        return octets[0]==10 || (octets[0]==172 && octets[1]>=16 && octets[1]<=31) || (octets[0]==192 && octets[1]==168);
    }
    private LocalNetwork(){}
}
