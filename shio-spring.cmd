@rem Run the Shio Spring Boot backend, skipping the (slow) Node/pnpm React build.
@rem `process-resources` first syncs the prebuilt React console
@rem (shio-react -> src/main/resources/public) into target/classes, so the SPA
@rem forward for /login, /console, /setup resolves /index.html (otherwise 404).
@rem Build the React app at least once (`pnpm -C shio-react run compile:build`)
@rem before relying on the skip flags here.
call mvn process-resources spring-boot:run -pl shio-app -Dskip.installnodepnpm=true -Dskip.pnpm=true
