@rem Run the Shio Spring Boot backend (mirrors turing-spring.cmd).
@rem The React console is built by the frontend-maven-plugin in generate-resources
@rem and served from classpath:/public/ — maven-resources overwrite=true guarantees
@rem index.html lands in target/classes. Pass -Dskip.npm=true to skip the React
@rem build and run against a previously-built src/main/resources/public.
call mvn spring-boot:run -pl shio-app -Dskip.npm -DskipTests -Dmaven.test.skip=true
