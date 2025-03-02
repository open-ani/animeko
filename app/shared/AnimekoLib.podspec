Pod::Spec.new do |spec|
    spec.name                     = 'AnimekoLib'
    spec.version                  = '3.0.0-dev'
    spec.homepage                 = 'https://github.com/open-ani/animeko'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'Animeko Shared Library'
    spec.vendored_frameworks      = 'build/cocoapods/framework/AnimekoFramework.framework'
    spec.libraries                = 'c++'
                
    spec.dependency 'Sentry', '~> 8.44.0'
                
    if !Dir.exist?('build/cocoapods/framework/AnimekoFramework.framework') || Dir.empty?('build/cocoapods/framework/AnimekoFramework.framework')
        raise "

        Kotlin framework 'AnimekoFramework' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:

            ./gradlew :app:shared:generateDummyFramework

        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
                
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
                
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':app:shared',
        'PRODUCT_MODULE_NAME' => 'AnimekoFramework',
    }
                
    spec.script_phases = [
        {
            :name => 'Build AnimekoLib',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                  exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
    spec.resources = ['build/compose/cocoapods/compose-resources']
end