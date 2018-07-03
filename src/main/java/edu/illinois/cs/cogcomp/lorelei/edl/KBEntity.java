package edu.illinois.cs.cogcomp.lorelei.edl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* Storage class for data corresponding to 
* and entity in the KB to be linked to.
**/
public class KBEntity{

  private static Logger logger = LoggerFactory.getLogger(KBEntity.class);
  /**
  * @param attributes string array of data from KB that defines
  *                   the entity.
  * TODO: Need to add the rest of the data.
  **/
  public KBEntity(String[] attributes){
    origin = attributes[0];
    type = attributes[1];
    entityID = attributes[2];
    nameUTF8 = attributes[3];
    if(!origin.equals("AUG")){
      nameASCII = attributes[4];
      lat = attributes[5];
      lon = attributes[6];
      featureClass = attributes[7];
      //featureClassName = attributes[8];
      featureCode = attributes[9];
      featureCodeName = attributes[10];
      featureCodeDesc = attributes[11];
      countryCode = attributes[12];
      logger.debug("origin: " + origin);
      logger.debug("length of attributes: " + attributes.length);

      if(attributes.length == TOTAL_NUM_ATTRIBS)
        externalLink = attributes[TOTAL_NUM_ATTRIBS - 1];
      else
        externalLink = NO_EXT_LINK;
    }
    //attributes[13];
    //attributes[14];
    //attributes[15];
    //attributes[16];
    //attributes[17];
    //attributes[18];
    //attributes[19];
    //attributes[20];
    //attributes[21];
    //attributes[22];
    //attributes[23];
    //attributes[24];
    //attributes[25];
    //attributes[26];
    wikiPrior = 0.0;
    popScore = 0.0;
    jaroScore = 0.0;
  }

  public String toString(){
    return String.format("%s\t%s\t%.2f\t%.2f",
                         entityID,nameUTF8,popScore,jaroScore);
  }

  @Override
  public boolean equals(Object o){
    if( this == o) return true;
    if(!(o instanceof KBEntity)) return false;
    KBEntity other = (KBEntity)o;
    return this.entityID.equals(other.getEntityID());
  }

  public void calcPopSimPrior(){
    this.prior = this.jaroScore*this.popScore;
  }

  public String getOrigin(){return origin;}
  public String getType(){return type;}
  public String getEntityID(){return entityID;}
  public String getNameUTF8(){return nameUTF8;}
  public String getNameASCII(){return nameASCII;}
  public String getLat(){return lat;}
  public String getLong(){return lon;}
  public String getCountryCode(){return countryCode;}
  public String getFeatureClass(){return featureClass;}
  public String getFeatureClassName(){return featureClassName;}
  public String getFeatureCode(){return featureCode;}
  public String getFeatureCodeName(){return featureCodeName;}
  public String getFeatureCodeDesc(){return featureCodeDesc;}
  public String getExternalLink(){return externalLink;}
  public double getPopScore(){return popScore;}
  public double getJaroScore(){return jaroScore;}
  public double getPrior() {return this.jaroScore*this.popScore;}

  public int getNumAltNames(){return numAltNames;}
  public boolean inWiki(){return externalLink.contains("wikipedia");}
  
  public void setOrigin(String origin){ this.origin = origin;}
  public void setEntityID(String entityID){this.entityID=entityID;}
  public void setType(String type){this.type = type;}
  public void setNameUTF8(String nameUTF8){this.nameUTF8 = nameUTF8;}
  public void setNameASCII(String nameASCII){this.nameASCII=nameASCII;}
  public void setLat(String lat){this.lat = lat;}
  public void setLon(String lon){this.lon = lon;}
  public void setCountryCode(String cc){this.countryCode = cc;}

  public void setPopScore(double score){this.popScore = score;}
  public void setJaroScore(double score){this.jaroScore = score;}
  public void setNumAltNames(int numAltNames){ this.numAltNames = numAltNames;}
  
  private String origin;
  private String type;
  private String entityID;
  private String nameASCII;
  private String nameUTF8;
  private String lat;
  private String lon;
  private String featureClass;
  private String featureClassName;
  private String featureCode;
  private String featureCodeName;
  private String featureCodeDesc;
  private String countryCode;
  private String externalLink;

  private int numAltNames;
  private double wikiPrior;
  private double popScore;
  private double jaroScore;
  private double prior;

  private int TOTAL_NUM_ATTRIBS = 47;
  private String NO_EXT_LINK = "";
//feature_code_description        
//country_code_name       
//cc2     
//admin1_code     
//admin1_code_name        
//admin2_code     
//admin2_code_name        
//admin3_code     
//admin4_code     
//population      
//elevation       
//dem     
//timezone        
//modification_date       
//per_gpe_loc_of_association      
//per_title_or_position   
//per_org_of_association  
//per_role_in_incident    
//per_year_of_birth       
//per_year_of_death       
//per_gender      
//per_family_member       
//note    
//aim     
//org_date_established    
//date_established_note   
//org_website     
//org_gpe_loc_of_association      
//org_members_employees_per       
//org_parent_org  
//executive_board_members 
//jurisdiction    
//trusteeship_council     
//national_societies      
//external_link
 } 
