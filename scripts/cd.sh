#!/bin/bash

set -e

OFFSET=232 # last build number in Teamcity
export BUILD_NUM=${{ github.GITHUB_RUN_NUMBER }} + $OFFSET

npm test
npm build

declare -x SBT_OPTS="-Dbuild.number=${BUILD_NUM}"
sbt clean; compile; test; rpm:packageBin

echo "skipping s3 upload for initial test"
#aws s3 cp target/rpm/RPMS/noarch/vaultdoor-1.0-${BUILD_NUM}.noarch.rpm s3://gnm-multimedia-deployables/vaultdoor/${BUILD_NUM}/vaultdoor-1.0-${BUILD_NUM}.noarch.rpm --acl public-read
