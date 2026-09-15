package com.bigphil.mergehell.persistence;
import com.bigphil.mergehell.combat.*;
import com.bigphil.mergehell.engine.*;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.*;
import com.bigphil.mergehell.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SegmentCheckpointTest {
    @Test void allFiveSectionsRoundTripIdentityResourcesDirectorAndWorldFlags() {
        for(int chapter=0;chapter<5;chapter++) {
            GameSession session=new GameSession(18);session.beginMission(chapter);
            session.runBuild().setIdentity(CharacterId.ENGINEER,GameDifficulty.CHALLENGE);
            session.tick(new InputFrame(true,false,false,false,false,false));
            session.directMission(new com.bigphil.mergehell.mission.DirectorInput(1,100,.5,0));
            Player player=new Player(3105,450);player.bindRunBuild(session.runBuild());player.useBomb();
            var route=chapter>=2?new ChapterRouteController(chapter,480):null;
            var nav=new CheckpointCodec.Navigation(3105,Set.of(0),route==null?Map.of():route.savedHealth(),
                    route==null?Map.of():route.savedHatches(),Set.of(),Set.of(0,2));
            var saved=CheckpointCodec.captureSegment(UUID.randomUUID().toString(),1400,5000,session,player.checkpointForNextLevel(),nav);
            var storage=new MergeHellState();storage.activeRun=saved;
            storage.unlockedCharacters.add(CharacterId.ENGINEER);
            storage.rankedScores.put(GameDifficulty.CHALLENGE,List.of(1400));
            var xml=com.intellij.util.xmlb.XmlSerializer.serialize(storage);
            var decoded=com.intellij.util.xmlb.XmlSerializer.deserialize(xml,MergeHellState.class);
            assertTrue(decoded.unlockedCharacters.contains(CharacterId.ENGINEER));
            assertEquals(List.of(1400),decoded.rankedScores.get(GameDifficulty.CHALLENGE));
            var copied=new StateMigrator(()->"").migrate(decoded).activeRun;
            assertNotNull(copied);assertNotSame(saved,copied);
            var restored=CheckpointCodec.restoreSession(copied);
            assertEquals(CharacterId.ENGINEER,restored.runBuild().character());assertEquals(GameDifficulty.CHALLENGE,restored.runBuild().difficulty());
            assertEquals(session.directorCheckpoint(),restored.directorCheckpoint());
            assertEquals(Set.of(0),copied.explorationVisited);assertEquals(nav.health(),copied.routeHealth);
            assertEquals(2,CheckpointCodec.playerCheckpoint(copied).bombs());assertEquals(60,CheckpointCodec.playerCheckpoint(copied).bombCooldown());
        }
    }
    @Test void malformedWorldProgressIsRejectedAndOldEntrancesKeepTheirDefaultIdentity() {
        GameSession session=new GameSession(4);Player player=new Player(100,480);
        var saved=CheckpointCodec.capture(UUID.randomUUID().toString(),0,5000,session,player.checkpoint());
        saved.session.character=null;saved.session.difficulty=null;
        var restored=CheckpointCodec.restoreSession(saved);
        assertEquals(CharacterId.REPAIR,restored.runBuild().character());assertEquals(GameDifficulty.STANDARD,restored.runBuild().difficulty());
        saved.checkpointVersion=3;saved.checkpointKind=CheckpointCodec.SAFE_SEGMENT;saved.checkpointY=450;
        assertThrows(RuntimeException.class,()->CheckpointCodec.validate(saved));
    }
    @Test void secretUnlockAndRankedScoresAreIndependentAndCopiesDoNotLeakMutations() {
        var service=MergeHellStateService.getInstance();var original=service.getState();
        try {
            service.loadState(new MergeHellState());assertTrue(service.discoverSecret(0));assertFalse(service.discoverSecret(0));
            assertTrue(service.getState().unlockedCharacters.contains(CharacterId.ENGINEER));
            service.recordScore(100,GameDifficulty.STANDARD);service.recordScore(200,GameDifficulty.CHALLENGE);
            assertEquals(List.of(100),service.getState().rankedScores.get(GameDifficulty.STANDARD));
            assertEquals(List.of(200),service.getState().rankedScores.get(GameDifficulty.CHALLENGE));
            var copy=service.getState();copy.rankedScores.get(GameDifficulty.STANDARD).clear();
            assertEquals(List.of(100),service.getState().rankedScores.get(GameDifficulty.STANDARD));
        } finally {service.loadState(original);}
    }
}
