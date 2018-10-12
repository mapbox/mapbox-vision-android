publish-local:
	# This publishes to ~/.m2/repository/com/mapbox/vision
	export IS_LOCAL_DEVELOPMENT=true; ./gradlew :MapboxVision:uploadArchives