# Dockerfile: builds the WAR, then runs it in Tomcat. Railway finds this file and uses it
# to build and run the app; "docker build" does the same on your machine.
#
# Two stages, so the image that runs holds Tomcat and the WAR, and not Maven or the source.

# STAGE 1, build: the same as running "mvn clean package" on your machine. The WAR has to
# be built here because target/ is in .gitignore, so it isn't in the repo Railway builds.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Download the dependencies before copying the source. Docker caches each step, so a
# change to a .java file reuses this layer instead of downloading everything again.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean package

# STAGE 2, run: the official Tomcat 10.1 image with JDK 21.
FROM tomcat:10.1-jdk21
# Naming it ROOT.war deploys the app at "/" instead of "/mini-procurement".
COPY --from=build /app/target/mini-procurement.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
# Railway says which port to listen on through the PORT environment variable, but Tomcat
# reads its port from conf/server.xml. So swap PORT in before starting (8080 if it isn't
# set). "exec" makes Tomcat the main process, so it receives Railway's stop signal.
CMD sed -i "s/port=\"8080\"/port=\"${PORT:-8080}\"/" conf/server.xml && exec catalina.sh run
