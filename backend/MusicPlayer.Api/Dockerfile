FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

COPY ["backend/MusicPlayer.Api/MusicPlayer.Api.csproj", "backend/MusicPlayer.Api/"]
RUN dotnet restore "backend/MusicPlayer.Api/MusicPlayer.Api.csproj"

COPY backend/MusicPlayer.Api/ backend/MusicPlayer.Api/
WORKDIR "/src/backend/MusicPlayer.Api"
RUN dotnet publish "MusicPlayer.Api.csproj" -c Release -o /app/publish /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS final
WORKDIR /app
EXPOSE 5116
ENV ASPNETCORE_URLS=http://+:5116
ENV ASPNETCORE_ENVIRONMENT=Development

COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "MusicPlayer.Api.dll"]

