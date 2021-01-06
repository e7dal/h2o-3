package ai.h2o.targetencoding;

import water.Iced;
import water.util.ArrayUtils;

import java.util.stream.IntStream;

/**
 * The interaction value is simply encoded as:
 * val = val1 + (val2 * card1) + … + (valN * card1 * … * cardN-1)
 * where val1, val2, …, valN are the interacting values
 * and card1, …, cardN are the extended domain cardinalities (taking NAs into account) for interacting columns.
 */
class InteractionsEncoder extends Iced {
    static final String UNSEEN = "_UNSEEN_";
    static final String NA = "_NA_";

    boolean _encodeUnseenAsNA;
    String[][] _interactingDomains;
    String[] _interactionDomain;
    int[] _encodingFactors;

    InteractionsEncoder(String[][] interactingDomains, boolean encodeUnseenAsNA) {
        _encodeUnseenAsNA = encodeUnseenAsNA;
        _interactingDomains = interactingDomains;
        _interactionDomain = createInteractionDomain();
        _encodingFactors = createEncodingFactors();
    }


    int encode(int[] interactingValues) {
        int value = 0;
        for (int i = 0; i < interactingValues.length; i++) {
            int domainCard = _interactingDomains[i].length;
            int interactionFactor = _encodingFactors[i];
            int ival = interactingValues[i];
            if (ival >= domainCard) ival = domainCard;  // unseen value during training
            if (ival < 0) ival = _encodeUnseenAsNA ? domainCard : (domainCard + 1);  // NA
            value += ival * interactionFactor;
        }
        return value;
    }

    int encodeStr(String[] interactingValues) {
        int[] values = new int[interactingValues.length];
        for (int i = 0; i < interactingValues.length; i++) {
            String[] domain = _interactingDomains[i];
            String val = interactingValues[i];
            int ival = val==null ? -1 : ArrayUtils.find(domain, val);
            if (ival < 0 && val != null) {  //emulates distinction between NA and unseen.
                values[i] = domain.length; 
            } else {
                values[i] = ival;
            }
        }
        return encode(values);
    }

    int[] decode(int interactionValue) {
        int[] values = new int[_encodingFactors.length];
        int value = interactionValue;
        for (int i = _encodingFactors.length - 1; i >= 0; i--) {
            int factor = _encodingFactors[i];
            values[i] = value / factor;
            value %= factor;
        }
        return values;
    }

    String[] decodeStr(int interactionValue) {
        int[] values = decode(interactionValue);
        String[] catValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String[] domain = _interactingDomains[i];
            int val = values[i];
            catValues[i] = val < domain.length ? domain[val]
                    : i==domain.length ? (_encodeUnseenAsNA ? null : UNSEEN)
                    : null;
        }
        return catValues;
    }

    private int[] createEncodingFactors() {
        int[] factors = new int[_interactingDomains.length];
        int multiplier = 1;
        for (int i = 0; i < _interactingDomains.length; i++) {
            int domainCard = _interactingDomains[i].length;
            int interactionFactor = _encodeUnseenAsNA ? (domainCard + 1) : (domainCard + 2);  // +1 for potential unseen values, +1 for NAs (see #interactionDomain)
            factors[i] = multiplier;
            multiplier *= interactionFactor;
        }
        return factors;
    }

    private String[] createInteractionDomain() {
        int card = 1;
        for (String[] domain : _interactingDomains)
            card *= (_encodeUnseenAsNA ? (domain.length + 1) : (domain.length + 2));  // +1 for potential unseen values, +1 for NAs
        return IntStream.range(0, card).mapToObj(Integer::toString).toArray(String[]::new);
    }

}