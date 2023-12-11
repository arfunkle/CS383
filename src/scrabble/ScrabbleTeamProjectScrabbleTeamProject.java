package scrabble;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.TreeMap;

public class ScrabbleTeamProjectScrabbleTeamProject implements ScrabbleAI {
    private class Move implements ScrabbleMove {
        /**
         * The word to be played.
         *
         * @see Board
         */
        public String word;

        /** The location of the first tile in the word (new or already on the board. */
        public Location location;

        /** The direction in which this word is to be played: Location.HORIZONTAL or Location.VERTICAL. */
        public Location direction;

        public Move(String word, Location location, Location direction) {
            this.word = word;
            this.location = location;
            this.direction = direction;
        }

        @Override
        public Location[] play(Board board, int playerNumber) throws IllegalMoveException {
            board.play(word, location, direction, board.getHand(playerNumber));
            return new Location[] {location, direction};
        }
    }

    private class Dawg {
        // Give the file path to words.txt
        public Dawg(String file) {
        }

        // Given a board state and a (player's) hand, return all possible legal moves that can be made
        public ArrayList<Move> findAllMoves(GateKeeper gateKeeper) {
        }
    }

    private GateKeeper gateKeeper;
    public ScrabbleTeamProjectScrabbleTeamProject() {


    }
    @Override
    public void setGateKeeper(GateKeeper gateKeeper) {
        this.gateKeeper = gateKeeper;
    }

    @Override
    public ScrabbleMove chooseMove() {
        Dawg dawg = new Dawg("words.txt");
        ArrayList<Move> moveList = dawg.findAllMoves(gateKeeper);
        Move finalMove = null;
        int finalMoveScore = 0;

        for (Move currentMove : moveList) {
            int currentMoveScore = gateKeeper.score(currentMove.word, currentMove.location, currentMove.direction);
            if (finalMoveScore < currentMoveScore) {
                finalMoveScore = currentMoveScore;
                finalMove = currentMove;
            }
        }

        return finalMove;
    }

    public static void main(String[] unused) {
        new ScrabbleTeamProjectScrabbleTeamProject();
    }
}
