#!/usr/bin/env bash

# ==================================================== #
echo "running release script"

# Networks this script checks for
NETWORKS=( 
    AdColony
    AdMob
    AppLovin 
    Chartboost
    FacebookAudienceNetwork
    Flurry 
    IronSource
    Tapjoy
    UnityAds
    Verizon
    Vungle
)

### Function to get display name for Firebase update ###
function get_display_name {
    key=$1
    out=$2
    name=$key
    case "$key" in
        AdMob ) name="Google (AdMob)";;
        FacebookAudienceNetwork ) name="Facebook Audience Network";;
        Flurry ) name="Yahoo! Flurry";;
        IronSource ) name="ironSource";;
        OnebyAOL ) name="One by AOL";;
        UnityAds ) name="Unity Ads";;
    esac
    eval "$out='$name'"
}

### Function to read Adapter version from AdapterConfiguration ###
function read_networkAdapter_version
{
 versionnumber=`grep -r "project.version = " ./$1/ | awk '{print $3}' | sed s/\'//g | sed s/\;//g`
 echo $versionnumber
 sdkverion=`echo $versionnumber | cut -d'.' -f 1-3`
 echo $sdkverion
 lowercaseselection=$(echo "$1" | tr '[:upper:]' '[:lower:]')
 mv ./libs/mopub-$lowercaseselection-adapters-*.aar ./libs/$lowercaseselection-$versionnumber.aar
 
 ### Generate pom file for adapter version ###
echo '<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.mopub.mediation</groupId>
  <artifactId>'"$lowercaseselection"'</artifactId>
  <version>'"$versionnumber"'</version>
  <packaging>aar</packaging>
</project>' >> ./libs/sample.pom
  echo $lowercaseselection
  echo $versionnumber
  mv ./libs/*.pom ./libs/$lowercaseselection-$versionnumber.pom

  ### CREATING TAG RELEASE IN GITHUB ###
  commitId= git rev-parse HEAD
  echo $commitId
  tagname="$lowercaseselection-$versionnumber"
  echo $tagname

  ### Publish release in Github
  curl -H "Authorization: token ${GITHUB_KEY}" --data '{"tag_name": "'"$tagname"'","target_commitish": "'"$commitId"'","name": "'"$versionnumber"'","body": "Refer https://github.com/mopub/mopub-android-mediation/blob/master/'"$1"'/CHANGELOG.md.","draft": false,"prerelease": false}' https://api.github.com/repos/mopub/android-mediation/releases

  ### RELEASING aar AND pom TO BINTRAY ###
  #curl -T ./libs/$lowercaseselection-$versionnumber.aar -u${USER_NAME_MOPUB}:${BINTRAY_MOPUB} https://api.bintray.com/content/mopub/mopub-android-mediation/com.mopub.mediation.$lowercaseselection/$versionnumber/com/mopub/mediation/$lowercaseselection/$versionnumber/
  #curl -T ./libs/$lowercaseselection-$versionnumber.pom -u${USER_NAME_MOPUB}:${BINTRAY_MOPUB} https://api.bintray.com/content/mopub/mopub-android-mediation/com.mopub.mediation.$lowercaseselection/$versionnumber/com/mopub/mediation/$lowercaseselection/$versionnumber/
  if [ $? -eq 0 ]; then
    ### UPDATE FIREBASE ###
    echo "Updating firebase JSON..."
    firebase_project="mopub-mediation"
    get_display_name $i name
    json_path="/releaseInfo/$name/Android/version"

    echo $i
    echo $versionnumber
    if [ -z "$FIREBASE_ACCESS" ]; then
        print_red_line "\$FIREBASE_ACCESS environment variable not set!"
    else
        #firebase database:set --confirm "/releaseInfo/$name/Android/version/adapter/" --data "\"$versionnumber\"" --project $firebase_project --token ${FIREBASE_TOKEN}
        #firebase database:set --confirm "/releaseInfo/$name/Android/version/sdk/" --data "\"$sdkverion\"" --project $firebase_project --token ${FIREBASE_TOKEN}
        if [[ $? -ne 0 ]]; then
            echo "ERROR: Failed to run firebase commands; please follow instructions at: https://firebase.google.com/docs/cli/"
        else
            echo "Done updating firebase JSON"
        fi
      fi
else
    echo Failed to Push to bintray. Please update bintray before updating Firebase.
fi

### CLEAN UP /LIBS FOLDER ###
 rm -r ./libs/*.pom

}

for i in "${NETWORKS[@]}"
do
    changed=$(git log --name-status -1 --oneline ./ | grep $i)
    if [[ ! -z "$changed" ]]; then
        echo "$changed"
        read_networkAdapter_version  $i
    fi  
done