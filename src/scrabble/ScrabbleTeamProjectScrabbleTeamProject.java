package scrabble;

import edu.princeton.cs.algs4.In;

import java.util.*;

public class ScrabbleTeamProjectScrabbleTeamProject implements ScrabbleAI {

    // We are implementing a new version of PlayWord so that we can access the word, location,
    // and direction from outside the class.
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

    /****
     * DAWG - Directed Acyclic Word Graph
     * This is the core of our bot. We are storing all valid words within it and then using the efficiency of it to
     * find all valid moves rapidly.
     */
    private class Dawg {
        // Vertex of a word tree
        private class Dawg_v {
            public boolean isWord;
            public Map<Character, Dawg_v> children;

            public Dawg_v(boolean isWord) {
                this.isWord = isWord;
                this.children = new HashMap();
            }
        }

        // A word tree
        // Just stores the head vertex of the tree,
        // but all of the verification-related methods are also here
        private class Dawg_tree {
            public Dawg_v tree = new Dawg_v(false);

            // Stores all the words from the file within the tree.
            public Dawg_tree(In input) {
                for (String word : input.readAllLines()) {
                    Dawg_v head = tree;
                    for (char letter : word.toCharArray()) {
                        head.children.putIfAbsent(letter, new Dawg_v(false));
                        head = head.children.get(letter);
                    }
                    head.isWord = true;
                }
            }

            // Verifies word is in the DAWG
            public boolean verify(String word) {
                Dawg_v head = tree;
                for (char letter : word.toCharArray()) {
                    if (!head.children.containsKey(letter))
                        return false;
                    head = head.children.get(letter);
                }
                return head.isWord;
            }
        }

        private Dawg_tree tree; // Main tree as loaded from words.txt
        private int[][] rows; // All 30 rows/columns filled out with word "potentials"

        // Special mask flags that we need
        private static final char BLANK = 'z' + 1; // We have at least one blank tile in our hand
        private static final char ANCHOR = 'z' + 2; // This character is an anchor
        private static final char EMPTY = 'z' + 3; // This character can be empty, meaning the word can end here
        private static final char STATIC = 'z' + 4; // This character can not be changed

        public Dawg(String file) {
            this.tree = new Dawg_tree(new In(file));
            rows = new int[30][15];
        }

        // Basically just a very compact way of representing Map<Character, Boolean>
        private static int toMask(char ch) {
            return 1 << (ch - 'a');
        }

        private static boolean maskContains(int mask, char ch) {
            int result = toMask(ch) & mask;
            return result != 0;
        }

        // Is the tile "empty" (in the eyes of spelling words, so oob is empty in this case)
        private static boolean isEmpty(GateKeeper board, int x, int y) {
            Location loc = new Location(y, x);
            if (!loc.isOnBoard()) return true;
            char ch = board.getSquare(loc);
            return " -+=#".indexOf(ch) != -1;
        }

        // Read the columns of the board into rows[][]
        private void getCols(GateKeeper board, int subset) {
            for (int x = 0; x < 15; x++) {
                for (int y = 0; y < 15; y++) {
                    char ch = board.getSquare(new Location(y, x));
                    if (!isEmpty(board, x, y)) // If there's already a tile on the board here, it will be a static anchor
                        rows[x][y] = toMask(ch) | toMask(ANCHOR) | toMask(STATIC);
                    else { // If there's not a tile on the board here
                        rows[x][y] = toMask(EMPTY); // We could keep it that way
                        if (isEmpty(board, x - 1, y) && isEmpty(board, x + 1, y))
                            rows[x][y] |= subset; // If there's no perpendicular restrictions, we could theoretically play anything from our hand here
                        else { // Putting a word here would add on to an existing word perpendicularly to it
                            rows[x][y] |= toMask(ANCHOR); // So if we put a tile here we wil be anchored
                            int mx, nx;
                            for (mx = 0; !isEmpty(board, x - mx - 1, y); mx++) {}
                            String word = "";
                            for (int l = 0; !isEmpty(board, x - mx + l, y); l++) // Read the word as it is
                                word += board.getSquare(new Location(y, x - mx + l));
                            for (nx = 0; !isEmpty(board, y, x + nx + 1); nx++) {}
                            for (int l = nx-1; !isEmpty(board, y, x + nx - l); l--)
                                word += board.getSquare(new Location(x + nx - l, y));
                            for (char letter : "abcdefghijklmnopqrstuvwxyz".toCharArray())
                                if (maskContains(subset, letter) || maskContains(subset, BLANK)) {
                                    if (word.length()>mx)
                                        word = word.substring(0, mx) + letter + word.substring(mx+1);
                                    else
                                        word = word.substring(0, mx) + letter;
                                    if (tree.verify(word))
                                        rows[x+15][y] |= toMask(letter);
                                }
                        }
                    }
                }
            }
            rows[7][7] |= toMask(ANCHOR); // Middle square is an anchor (only comes up on the first turn, though)
        }

        // Read the rows of the board into rows[][] - see above getCols()
        private void getRows(GateKeeper board, int subset) {
            numS = 0;
            for (int x = 0; x < 15; x++) {
                for (int y = 0; y < 15; y++) {
                    char ch = board.getSquare(new Location(x, y));
                    if (ch == 's')
                        numS++;
                    if (!isEmpty(board, y, x))
                        rows[x+15][y] = toMask(ch) | toMask(ANCHOR) | toMask(STATIC);
                    else {
                        rows[x+15][y] = toMask(EMPTY);
                        if (isEmpty(board, y, x - 1) && isEmpty(board, y, x + 1))
                            rows[x+15][y] |= subset;
                        else {
                            rows[x+15][y] |= toMask(ANCHOR);
                            int mx, nx;
                            for (mx = 0; !isEmpty(board, y, x - mx - 1); mx++) {}
                            String word = "";
                            for (int l = 0; !isEmpty(board, y, x - mx + l); l++)
                                word += board.getSquare(new Location(x - mx + l, y));
                            for (nx = 0; !isEmpty(board, y, x + nx + 1); nx++) {}
                            for (int l = nx-1; !isEmpty(board, y, x + nx - l); l--)
                                word += board.getSquare(new Location(x + nx - l, y));
                            for (char letter : "abcdefghijklmnopqrstuvwxyz".toCharArray())
                                if (maskContains(subset, letter) || maskContains(subset, BLANK)) {
                                    if (word.length()>mx)
                                        word = word.substring(0, mx) + letter + word.substring(mx+1);
                                    else
                                        word = word.substring(0, mx) + letter;
                                    if (tree.verify(word))
                                        rows[x+15][y] |= toMask(letter);
                                }
                        }
                    }
                }
            }
            rows[7+15][7] |= toMask(ANCHOR);
        }

        // Basically traverse all possible combinations of letters that we could play down a row, and append all of the ones that work to moves
        private void findAllWordsInRow(
                ArrayList<Move> moves,
                Move move,
                String word,
                boolean anchored,
                int[] hand,
                Dawg_v head,
                ArrayList<Integer> remaining
        ) {
            boolean canEnd = true;
            if (remaining.size() > 0) // If the next tile can be empty (or there is no next tile, i.e. off the board) we can end the word here
                canEnd = maskContains(remaining.get(0), EMPTY);

            if (canEnd && anchored && head.isWord) // But of course we also need to consider that we are both anchored, and that we have made a real word
                moves.add(new Move(word, move.location, move.direction));

            if (remaining.size() > 0) { // Now let's add one more character
                anchored |= maskContains(remaining.get(0), ANCHOR); // If this next character is an anchor, then we'll be anchored
                for (char letter : "abcdefghijklmnopqrstuvwxyz".toCharArray()) {
                    if (maskContains(remaining.get(0), letter)) { // For each letter in the mask
                        // If we don't have the letter (or any blanks) and it's not already on the board, then we can't play it
                        if (hand[(int)(letter - 'a')] == 0 && hand[BLANK - 'a'] == 0 && !maskContains(remaining.get(0), STATIC)) continue;
                        // If playing this letter would never lead to a word either, then we also don't bother
                        if (!head.children.containsKey(letter)) continue;
                        char letterToPlay = letter;
                        // If we can't play the letter, we use a blank
                        if (hand[(int)(letter - 'a')] == 0 && !maskContains(remaining.get(0), STATIC)) {
                            letterToPlay = Character.toUpperCase(letter);
                            letter = BLANK;
                        }
                        int[] newHand = hand.clone();
                        char newLetter = ' ';
                        // If we are putting down a letter, we take it out of our hand
                        if (!maskContains(remaining.get(0), STATIC)) {
                            newLetter = letter;
                            newHand[(int)(letter - 'a')]--;
                        }
                        findAllWordsInRow( // Recurse with that letter added to the partial word
                                moves,
                                move,
                                word + newLetter,
                                anchored,
                                newHand,
                                head.children.get(letterToPlay),
                                new ArrayList<Integer>(remaining.subList(1, remaining.size()))
                        );
                    }
                }
            }
        }

        // This is a helper method for dealing with blank tiles.
        // If we used blanks, though, there are going to be a bunch of nasty permutations,
        // so we have to go through them all to be sure we've captured every move.
        // This will maximize scoring as if we have a move with two possible placements for the blank
        // (for example: oCcur and ocCur), and one of the letters might be on a letter multiplier,
        // this accounts for both options.
        public ArrayList<Move> permuteMovesWithBlanks(ArrayList<Move> moves, ArrayList<Character> hand) {
            ArrayList<Move> permuteMoves = new ArrayList<>();
            for (Move move : moves) {
                int openBlanks = 0;
                for (char ch : hand) if (ch == ' ') openBlanks++;
                String playedBlanks = "";
                for (int i = 0; i < move.word.length(); i++)
                    if (Character.isUpperCase(move.word.charAt(i))) {
                        openBlanks--;
                        playedBlanks = playedBlanks + Character.toLowerCase(move.word.charAt(i));
                    }
                String word = move.word.toLowerCase();
                switch (playedBlanks.length()) {

                    case 0:
                        permuteMoves.add(move);
                        if (openBlanks > 0) {
                            for (int i = 0; i < word.length(); i++) {
                                String newWord = word.substring(0, i) + Character.toUpperCase(word.charAt(i)) + word.substring(i + 1);
                                permuteMoves.add(new Move(newWord, move.location, move.direction));
                                if (openBlanks == 2) for (int j = i; j < word.length(); j++) {
                                    if (j != i) {
                                        String newerWord = newWord.substring(0, j) + Character.toUpperCase(newWord.charAt(j)) + newWord.substring(j + 1);
                                        permuteMoves.add(new Move(newerWord, move.location, move.direction));
                                    }
                                }
                            }
                        }
                        break;
                    case 1:
                        for (int i = 0; i < word.length(); i++) {
                            if (word.charAt(i) == playedBlanks.charAt(0)) {
                                String newWord = word.substring(0, i) + Character.toUpperCase(word.charAt(i)) + word.substring(i + 1);
                                permuteMoves.add(new Move(newWord, move.location, move.direction));
                                if (openBlanks == 1) {
                                    for (int j = 0; j < word.length(); j++) {
                                        if (Character.toLowerCase(word.charAt(j)) != playedBlanks.charAt(0) || j > i) {
                                            String newerWord = newWord.substring(0, j) + Character.toUpperCase(newWord.charAt(j)) + newWord.substring(j + 1);
                                            permuteMoves.add(new Move(newerWord, move.location, move.direction));
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    case 2:
                        for (int i = 0; i < word.length(); i++)
                            for (int j = 0; j < word.length(); j++) {
                                if (word.charAt(i) == playedBlanks.charAt(0) && word.charAt(j) == playedBlanks.charAt(1)) {
                                    if (playedBlanks.charAt(0) != playedBlanks.charAt(1) || j > i) {
                                        String newWord = word.substring(0, i) + Character.toUpperCase(word.charAt(i)) + word.substring(i + 1);
                                        String newerWord = newWord.substring(0, j) + Character.toUpperCase(newWord.charAt(j)) + newWord.substring(j + 1);
                                        permuteMoves.add(new Move(newerWord, move.location, move.direction));
                                    }
                                }
                            }
                        break;
                }
            }
            return permuteMoves;
        }

        // This method finds all valid moves on the board and returns an ArrayList of each move.
        public ArrayList<Move> findAllMoves(GateKeeper board) {
            // Fill rows[][] with up-to-date information
            ArrayList<Character> hand = board.getHand();
            int subset = 0;
            for (char letter : hand)
                if (letter == ' ') subset |= toMask(BLANK);
                else subset |= toMask(letter);
            this.getRows(board, subset);
            this.getCols(board, subset);

            ArrayList<Move> moves = new ArrayList<>();
            // Basically findAllWordsInRow() for every possible square/starting combination
            for (int x = 0; x < 15; x++) {
                for (int y = 0; y < 15; y++) {
                    Move moveC = new Move("", new Location(y, x), new Location(1, 0));
                    Move moveR = new Move("", new Location(x, y), new Location(0, 1));
                    int[] letters = new int[27];
                    for (char ch : hand)
                        if (ch == ' ')
                            letters[26]++;
                        else {
                            if (ch >= 'a')
                                letters[(int) (ch - 'a')]++;
                            if (ch == 's')
                                numS++;
                        }

                    ArrayList<Integer> remainingC = new ArrayList<>();
                    ArrayList<Integer> remainingR = new ArrayList<>();
                    for (int i = y; i < 15; i++) {
                        remainingC.add(rows[x][i]);
                        remainingR.add(rows[x + 15][i]);
                    }
                    findAllWordsInRow(moves, moveC, "", false, letters.clone(), this.tree.tree, remainingC);
                    findAllWordsInRow(moves, moveR, "", false, letters.clone(), this.tree.tree, remainingR);
                }
            }

            return permuteMovesWithBlanks(moves, hand);
        }
    }

    private static final boolean[] ALL_TILES = {true, true, true, true, true, true, true};

    private int numS = 0;

    private Dawg dawg;

    private GateKeeper gateKeeper;
    public ScrabbleTeamProjectScrabbleTeamProject() {
        dawg = new Dawg("words.txt");

    }
    @Override
    public void setGateKeeper(GateKeeper gateKeeper) {
        this.gateKeeper = gateKeeper;
    }

    /****
     * Uses our DAWG to calculate all possible moves before sending back the highest scoring one.
     * @return returns the highest scoring move in the form of a PlayWord object or an ExchangeTiles object for all our tiles.
     */
    @Override
    public ScrabbleMove chooseMove() {
        ArrayList<Move> moveList = dawg.findAllMoves(gateKeeper);
        Move finalMove = null;
        int finalMoveScore = 0;

        for (Move currentMove : moveList) {
            try {
                gateKeeper.verifyLegality(currentMove.word, currentMove.location, currentMove.direction);
                int currentMoveScore = gateKeeper.score(currentMove.word, currentMove.location, currentMove.direction);
                if (finalMoveScore < currentMoveScore) {
                    finalMoveScore = currentMoveScore;
                    finalMove = currentMove;
                }
            }
            catch (IllegalMoveException e) {
                // continue to next move
            }
        }


        if (finalMove != null)
            return new PlayWord(finalMove.word, finalMove.location, finalMove.direction);
        else
            return new ExchangeTiles(ALL_TILES);
    }

    // NOT USED - Didn't make any noticeable difference
    // Used to check if blanks
    private boolean confirmWordWithBlanksIsHighValue(Move currentMove, int currentMoveScore) {
        if (currentMoveScore > 50) return true;
        for (char ch : currentMove.word.toCharArray()) {
            if (ch >= 65 && ch <= 90) {
                return false;
            }
        }
        return true;
    }

    // NOT USED - Didn't make any noticeable difference
    // This method makes sure that we aren't setting the other player up for an easy plural play.
    private boolean confirmNotSettingUpPlural(Move currentMove, int currentMoveScore) {
        if (numS == 4) return true;
        StringBuilder newWord = new StringBuilder(currentMove.word);
        newWord.append("s");
        try {
            gateKeeper.verifyLegality(newWord.toString(), currentMove.location, currentMove.direction);
            int nextMoveScore = gateKeeper.score(newWord.toString(), currentMove.location, currentMove.direction);
            if (nextMoveScore > currentMoveScore+2) return false;
            else return true;
        }
        catch (IllegalMoveException e) {
            return true;
        }
    }

    public static void main(String[] unused) {
        new ScrabbleTeamProjectScrabbleTeamProject();
    }
}
